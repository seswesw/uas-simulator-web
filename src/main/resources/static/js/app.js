const API = '/api';
const HOME = [55.751244, 37.618423];

let monitorMap, droneMarker, routeLayer, waypointMarkers = [];
let monitorWeatherOverlay = null;
let monitorFollowControl = null;
let monitorFollowButton = null;
let followDroneEnabled = false;
let currentFollowSessionId = null;
let lastCameraMoveTime = 0;
let monitorProgrammaticMove = false;
let monitorProgrammaticMoveTimer = null;
let missionMap, missionRouteLayer, missionMarkers = [];
let missionWeatherOverlay = null;
let missionPreviewRouteLayer = null;
let missionPreviewMarkers = [];
let missionStartMarker = null;
let missionPreviewStartMarker = null;
let dronePickMap, dronePickMarker;
let stompClient = null;
let activeSessionId = null;
let selectedSessionId = null;
let altitudeChart, speedChart;
let lastTelemetry = null;
let cachedDrones = [];
let currentUser = loadCurrentUser();
const chartData = { labels: [], altitude: [], speed: [] };


const WEATHER_TYPES = {
    CLEAR: {
        name: 'Ясно',
        color: '#22c55e',
        windSpeedMs: 2.0,
        precipitationMmH: 0.0,
        visibilityKm: 10.0,
        speedMultiplier: 1.0,
        accelerationMultiplier: 1.0,
        batteryDrainMultiplier: 1.0,
        risk: 0
    },
    WIND: {
        name: 'Сильный ветер',
        color: '#f59e0b',
        windSpeedMs: 12.0,
        precipitationMmH: 0.0,
        visibilityKm: 8.0,
        speedMultiplier: 0.78,
        accelerationMultiplier: 0.82,
        batteryDrainMultiplier: 1.35,
        risk: 2
    },
    RAIN: {
        name: 'Дождь',
        color: '#3b82f6',
        windSpeedMs: 6.5,
        precipitationMmH: 3.2,
        visibilityKm: 7.0,
        speedMultiplier: 0.86,
        accelerationMultiplier: 0.90,
        batteryDrainMultiplier: 1.25,
        risk: 1
    },
    FOG: {
        name: 'Туман',
        color: '#94a3b8',
        windSpeedMs: 2.5,
        precipitationMmH: 0.2,
        visibilityKm: 1.2,
        speedMultiplier: 0.70,
        accelerationMultiplier: 0.78,
        batteryDrainMultiplier: 1.18,
        risk: 2
    },
    STORM: {
        name: 'Гроза',
        color: '#ef4444',
        windSpeedMs: 17.0,
        precipitationMmH: 8.0,
        visibilityKm: 3.0,
        speedMultiplier: 0.52,
        accelerationMultiplier: 0.62,
        batteryDrainMultiplier: 1.85,
        risk: 3
    }
};

// Размер одной ячейки демонстрационной погодной карты.
// Погода теперь покрывает не только несколько кругов около старта, а всю видимую карту.
const WEATHER_GRID_DEGREES = 0.012;
const WEATHER_ROUTE_SAMPLE_METERS = 200;
const WEATHER_ROUTE_MAX_SAMPLES_PER_SEGMENT = 80;
const FOLLOW_CAMERA_MIN_INTERVAL_MS = 750;

const views = {
    dashboard: { title: 'Панель управления', el: 'view-dashboard' },
    monitor: { title: 'Мониторинг полёта', el: 'view-monitor' },
    drones: { title: 'Реестр БПЛА', el: 'view-drones' },
    missions: { title: 'Планирование миссий', el: 'view-missions' },
    alerts: { title: 'Журнал оповещений', el: 'view-alerts' },
    admin: { title: 'Администрирование', el: 'view-admin' }
};

document.querySelectorAll('.nav-btn').forEach(btn => {
    btn.addEventListener('click', () => switchView(btn.dataset.view));
});

document.getElementById('btnCloseModal').addEventListener('click', () => document.getElementById('droneModal').close());
document.getElementById('droneForm').addEventListener('submit', createDrone);
document.getElementById('connectForm').addEventListener('submit', submitConnect);
document.getElementById('missionForm').addEventListener('submit', createMission);
document.getElementById('missionDroneSelect').addEventListener('change', () => {
    setWaypointSpeedsFromSelectedDrone(true);
    syncMissionMapFromForm();
});
document.getElementById('btnAddWaypoint').addEventListener('click', () => addWaypointRow());
document.getElementById('btnClearWaypoints').addEventListener('click', clearWaypoints);
document.getElementById('btnAddDrone').addEventListener('click', () => {
    document.getElementById('droneModal').showModal();
    setTimeout(initDronePickMap, 150);
});
document.getElementById('btnStart').addEventListener('click', () => controlSession('start'));
document.getElementById('btnPause').addEventListener('click', () => controlSession('pause'));
document.getElementById('btnStop').addEventListener('click', () => controlSession('stop'));
document.getElementById('btnEmergency').addEventListener('click', () => controlSession('stop', true));
document.getElementById('btnLogin').addEventListener('click', () => document.getElementById('loginModal').showModal());
document.getElementById('btnLogout').addEventListener('click', logout);
document.getElementById('loginForm').addEventListener('submit', login);
document.getElementById('adminUserForm').addEventListener('submit', createAdminUser);
document.getElementById('btnRefreshActions').addEventListener('click', loadAdminActions);

function loadCurrentUser() {
    try {
        return JSON.parse(localStorage.getItem('uasCurrentUser')) || null;
    } catch (e) {
        return null;
    }
}

function saveCurrentUser(user) {
    currentUser = user;
    if (user) {
        localStorage.setItem('uasCurrentUser', JSON.stringify(user));
    } else {
        localStorage.removeItem('uasCurrentUser');
    }
    updateAuthUi();
}

function isAdmin() {
    return currentUser?.role === 'ADMIN';
}

function canModify() {
    return currentUser?.role === 'ADMIN' || currentUser?.role === 'OPERATOR';
}

async function api(path, options = {}) {
    const headers = { 'Content-Type': 'application/json', ...options.headers };
    if (currentUser?.id) {
        headers['X-User-Id'] = currentUser.id;
    }

    const res = await fetch(API + path, {
        headers,
        ...options
    });
    if (!res.ok) {
        const err = await res.json().catch(() => ({ error: res.statusText }));
        const message = err.error || 'Ошибка запроса';
        if (res.status === 401) {
            document.getElementById('loginModal').showModal();
        }
        throw new Error(message);
    }
    if (res.status === 204) return null;
    return res.json();
}

function switchView(name) {
    document.querySelectorAll('.nav-btn').forEach(b => b.classList.toggle('active', b.dataset.view === name));
    document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
    document.getElementById(views[name].el).classList.add('active');
    document.getElementById('pageTitle').textContent = views[name].title;
    if (name === 'monitor') openMonitorView();
    if (name === 'missions') setTimeout(initMissionMap, 100);
    if (name === 'admin' && !isAdmin()) {
        alert('Раздел доступен только администратору');
        switchView('dashboard');
        return;
    }
    refreshView(name);
}

function refreshView(name) {
    if (name === 'dashboard') loadDashboard();
    if (name === 'drones') loadDrones();
    if (name === 'missions') loadMissions();
    if (name === 'alerts') loadAlerts();
    if (name === 'admin') loadAdmin();
}


function openMonitorView() {
    requestAnimationFrame(() => {
        initMonitorMap();
        initChartsIfNeeded();
        fixMonitorLayout();
        restoreMonitorView();
    });
}

function fixMonitorLayout() {
    if (!monitorMap) return;
    monitorMap.invalidateSize(true);
    setTimeout(() => monitorMap.invalidateSize(true), 200);
    setTimeout(() => monitorMap.invalidateSize(true), 500);
    if (altitudeChart) {
        altitudeChart.resize();
        speedChart.resize();
    }
}

function createTileLayer() {
    // Светлый слой CARTO вместо стандартной карты OpenStreetMap.
    // На нём нет лишнего визуального значка/флага в углу, а маршрут и маркеры читаются лучше.
    return L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
        subdomains: 'abcd',
        maxZoom: 20,
        attribution: ''
    });
}

function weatherHash(latCell, lonCell) {
    const raw = Math.sin(latCell * 12.9898 + lonCell * 78.233) * 43758.5453;
    return raw - Math.floor(raw);
}

function weatherCodeForCell(latCell, lonCell) {
    const value = weatherHash(latCell, lonCell);
    if (value < 0.50) return 'CLEAR';
    if (value < 0.66) return 'RAIN';
    if (value < 0.81) return 'WIND';
    if (value < 0.93) return 'FOG';
    return 'STORM';
}

function weatherCellFor(latitude, longitude) {
    return {
        latCell: Math.floor(latitude / WEATHER_GRID_DEGREES),
        lonCell: Math.floor(longitude / WEATHER_GRID_DEGREES)
    };
}

function weatherCellBounds(latCell, lonCell) {
    const south = latCell * WEATHER_GRID_DEGREES;
    const north = (latCell + 1) * WEATHER_GRID_DEGREES;
    const west = lonCell * WEATHER_GRID_DEGREES;
    const east = (lonCell + 1) * WEATHER_GRID_DEGREES;
    return [[south, west], [north, east]];
}

function getWeatherAt(latitude, longitude) {
    if (!Number.isFinite(+latitude) || !Number.isFinite(+longitude)) {
        return { code: 'CLEAR', ...WEATHER_TYPES.CLEAR };
    }

    const cell = weatherCellFor(+latitude, +longitude);
    const code = weatherCodeForCell(cell.latCell, cell.lonCell);
    return { code, ...WEATHER_TYPES[code] };
}

function createWeatherMapOverlay(map, id) {
    const paneName = `weather-pane-${id}`;
    if (!map.getPane(paneName)) {
        map.createPane(paneName);
        map.getPane(paneName).style.zIndex = 350;
        map.getPane(paneName).style.pointerEvents = 'none';
    }

    const state = {
        id,
        map,
        paneName,
        layer: L.layerGroup(),
        visible: false,
        legend: null,
        control: null,
        button: null
    };

    state.redraw = () => redrawWeatherGrid(state);
    state.setVisible = visible => setWeatherOverlayVisible(state, visible);
    state.toggle = () => state.setVisible(!state.visible);

    addWeatherToggleControl(state);
    map.on('moveend zoomend resize', state.redraw);
    return state;
}

function addWeatherToggleControl(state) {
    const control = L.control({ position: 'topright' });
    control.onAdd = () => {
        const container = L.DomUtil.create('div', 'leaflet-bar weather-toggle-control');
        const button = L.DomUtil.create('a', 'weather-toggle-button', container);
        button.href = '#';
        button.role = 'button';
        button.title = 'Показать/скрыть карту погоды';
        button.textContent = 'Погода';

        L.DomEvent.disableClickPropagation(container);
        L.DomEvent.on(button, 'click', event => {
            L.DomEvent.preventDefault(event);
            state.toggle();
        });

        state.button = button;
        return container;
    };
    control.addTo(state.map);
    state.control = control;
}

function setWeatherOverlayVisible(state, visible) {
    if (!state?.map) return;
    state.visible = visible;

    if (visible) {
        if (!state.map.hasLayer(state.layer)) {
            state.layer.addTo(state.map);
        }
        redrawWeatherGrid(state);
        addWeatherLegend(state);
        state.button?.classList.add('active');
        state.button?.setAttribute('aria-pressed', 'true');
    } else {
        if (state.map.hasLayer(state.layer)) {
            state.map.removeLayer(state.layer);
        }
        removeWeatherLegend(state);
        state.button?.classList.remove('active');
        state.button?.setAttribute('aria-pressed', 'false');
    }
}

function redrawWeatherGrid(state) {
    if (!state?.visible || !state.map.hasLayer(state.layer)) return;

    state.layer.clearLayers();
    const bounds = state.map.getBounds().pad(0.15);
    const southCell = Math.floor(bounds.getSouth() / WEATHER_GRID_DEGREES) - 1;
    const northCell = Math.floor(bounds.getNorth() / WEATHER_GRID_DEGREES) + 1;
    const westCell = Math.floor(bounds.getWest() / WEATHER_GRID_DEGREES) - 1;
    const eastCell = Math.floor(bounds.getEast() / WEATHER_GRID_DEGREES) + 1;

    for (let latCell = southCell; latCell <= northCell; latCell++) {
        for (let lonCell = westCell; lonCell <= eastCell; lonCell++) {
            const code = weatherCodeForCell(latCell, lonCell);
            const weather = WEATHER_TYPES[code] || WEATHER_TYPES.CLEAR;
            const opacity = code === 'CLEAR' ? 0.07 : 0.22;
            L.rectangle(weatherCellBounds(latCell, lonCell), {
                pane: state.paneName,
                stroke: false,
                fillColor: weather.color,
                fillOpacity: opacity,
                interactive: false
            }).addTo(state.layer);
        }
    }
}

function addWeatherLegend(state) {
    if (state.legend) return;
    const legend = L.control({ position: 'bottomright' });
    legend.onAdd = () => {
        const div = L.DomUtil.create('div', 'weather-legend');
        div.innerHTML = weatherLegendHtml();
        return div;
    };
    legend.addTo(state.map);
    state.legend = legend;
}

function removeWeatherLegend(state) {
    if (!state.legend) return;
    state.map.removeControl(state.legend);
    state.legend = null;
}

function weatherLegendHtml() {
    return `
        <strong>Карта погоды</strong>
        ${Object.entries(WEATHER_TYPES)
            .map(([, w]) => `<span><i style="background:${w.color}"></i>${w.name}</span>`)
            .join('')}
        <small>Слой можно включать/выключать кнопкой «Погода». Расчёт маршрута выполняется после расстановки точек.</small>
    `;
}

function weatherPopupHtml(weather) {
    return `
        <strong>${weather.name}</strong><br>
        Ветер: ${weather.windSpeedMs.toFixed(1)} м/с<br>
        Осадки: ${weather.precipitationMmH.toFixed(1)} мм/ч<br>
        Видимость: ${weather.visibilityKm.toFixed(1)} км<br>
        Эффективная скорость: ${Math.round(weather.speedMultiplier * 100)}%<br>
        Расход батареи: ×${weather.batteryDrainMultiplier.toFixed(2)}
    `;
}

function weatherLabel(weather) {
    if (!weather) return '—';
    return `${weather.name} · ${Math.round(weather.speedMultiplier * 100)}% скорости · батарея ×${weather.batteryDrainMultiplier.toFixed(2)}`;
}

function makePoint(latitude, longitude) {
    return { latitude: +latitude, longitude: +longitude };
}

function isValidPoint(point) {
    return point
        && Number.isFinite(+point.latitude)
        && Number.isFinite(+point.longitude);
}

function interpolatePoint(a, b, t) {
    return {
        latitude: +a.latitude + (+b.latitude - +a.latitude) * t,
        longitude: +a.longitude + (+b.longitude - +a.longitude) * t
    };
}

function sampleWeatherAlongSegment(startPoint, endPoint) {
    const distance = distanceMeters(
        startPoint.latitude,
        startPoint.longitude,
        endPoint.latitude,
        endPoint.longitude
    );
    if (!Number.isFinite(distance) || distance <= 0) return [];

    const steps = Math.max(1, Math.min(
        WEATHER_ROUTE_MAX_SAMPLES_PER_SEGMENT,
        Math.ceil(distance / WEATHER_ROUTE_SAMPLE_METERS)
    ));

    const samples = [];
    for (let i = 0; i < steps; i++) {
        const fromT = i / steps;
        const toT = (i + 1) / steps;
        const midT = (fromT + toT) / 2;
        const from = interpolatePoint(startPoint, endPoint, fromT);
        const to = interpolatePoint(startPoint, endPoint, toT);
        const mid = interpolatePoint(startPoint, endPoint, midT);
        samples.push({
            from,
            to,
            mid,
            distanceMeters: distance / steps,
            weather: getWeatherAt(mid.latitude, mid.longitude)
        });
    }
    return samples;
}

function buildRoutePoints(drone, waypoints) {
    const points = [];
    if (drone && Number.isFinite(+drone.latitude) && Number.isFinite(+drone.longitude)) {
        points.push({ latitude: +drone.latitude, longitude: +drone.longitude, kind: 'start' });
    }
    waypoints.forEach((w, index) => {
        if (isValidPoint(w)) {
            points.push({ latitude: +w.latitude, longitude: +w.longitude, kind: 'waypoint', index });
        }
    });
    return points;
}

function routeBounds(points) {
    const valid = points.filter(isValidPoint).map(p => [p.latitude, p.longitude]);
    return valid.length ? L.latLngBounds(valid) : null;
}

function drawWeatherAwareRoute(map, points, options = {}) {
    const group = L.featureGroup().addTo(map);
    const weight = options.weight ?? 4;
    const opacity = options.opacity ?? 0.95;
    const dashArray = options.dashArray ?? null;

    for (let i = 1; i < points.length; i++) {
        const start = points[i - 1];
        const end = points[i];
        if (!isValidPoint(start) || !isValidPoint(end)) continue;

        const samples = sampleWeatherAlongSegment(start, end);
        samples.forEach(sample => {
            const weather = sample.weather;
            L.polyline(
                [[sample.from.latitude, sample.from.longitude], [sample.to.latitude, sample.to.longitude]],
                {
                    color: weather.color,
                    weight,
                    opacity,
                    dashArray
                }
            )
                .addTo(group)
                .bindPopup(`Участок маршрута<br>Погода: ${weatherLabel(weather)}<br>Длина участка: ${formatDistance(sample.distanceMeters)}`);
        });
    }
    return group;
}

function renderRouteWeatherDetails(estimate) {
    const el = document.getElementById('routeWeatherDetails');
    if (!el) return;

    if (!estimate) {
        el.innerHTML = 'Расставьте точки маршрута — погодный анализ появится здесь.';
        el.className = 'route-weather-details';
        return;
    }

    const rows = (estimate.weatherBreakdown || [])
        .filter(item => item.distanceMeters > 0)
        .map(item => {
            const weather = WEATHER_TYPES[item.code] || WEATHER_TYPES.CLEAR;
            const share = estimate.totalDistanceMeters > 0
                ? Math.round(item.distanceMeters / estimate.totalDistanceMeters * 100)
                : 0;
            return `<span><i style="background:${weather.color}"></i>${weather.name}: ${formatDistance(item.distanceMeters)} (${share}%)</span>`;
        })
        .join('');

    el.innerHTML = `
        <strong>Погодный анализ всего маршрута</strong>
        <small>Расчёт выполняется после расстановки точек: маршрут делится на участки примерно по ${WEATHER_ROUTE_SAMPLE_METERS} м, и для каждого участка берётся погода с общей погодной карты.</small>
        <div class="route-weather-breakdown">${rows || '<span>Нет данных по погоде</span>'}</div>
    `;
    el.className = 'route-weather-details ' + (estimate.maxWeatherRisk >= 3 ? 'weather-critical-box'
        : estimate.maxWeatherRisk >= 2 ? 'weather-warning-box' : '');
}

function initMonitorMap() {
    if (typeof L === 'undefined') {
        console.error('Leaflet не загружен');
        return;
    }
    if (monitorMap) return;
    const el = document.getElementById('map');
    if (!el) return;
    monitorMap = L.map('map', { preferCanvas: true, attributionControl: false }).setView(HOME, 13);
    createTileLayer().addTo(monitorMap);
    monitorWeatherOverlay = createWeatherMapOverlay(monitorMap, 'monitor');
    addMonitorFollowControl();

    monitorMap.on('dragstart zoomstart', () => {
        if (!monitorProgrammaticMove) {
            setFollowDrone(false);
        }
    });
}


function addMonitorFollowControl() {
    if (!monitorMap || monitorFollowControl) return;
    monitorFollowControl = L.control({ position: 'topright' });
    monitorFollowControl.onAdd = () => {
        const container = L.DomUtil.create('div', 'leaflet-bar camera-follow-control');
        const button = L.DomUtil.create('a', 'camera-follow-button', container);
        button.href = '#';
        button.role = 'button';
        button.title = 'Включить или выключить следование камеры за БПЛА';
        button.textContent = 'Следить';

        L.DomEvent.disableClickPropagation(container);
        L.DomEvent.on(button, 'click', event => {
            L.DomEvent.preventDefault(event);
            setFollowDrone(!followDroneEnabled, currentFollowSessionId || activeSessionId || selectedSessionId);
        });

        monitorFollowButton = button;
        updateFollowButtonState();
        return container;
    };
    monitorFollowControl.addTo(monitorMap);
}

function updateFollowButtonState() {
    if (!monitorFollowButton) return;
    monitorFollowButton.classList.toggle('active', followDroneEnabled);
    monitorFollowButton.setAttribute('aria-pressed', followDroneEnabled ? 'true' : 'false');
    monitorFollowButton.textContent = followDroneEnabled ? 'Слежение: ВКЛ' : 'Следить';
}

function setFollowDrone(enabled, sessionId = null) {
    followDroneEnabled = !!enabled;
    if (sessionId !== null && sessionId !== undefined) {
        currentFollowSessionId = +sessionId;
    }
    updateFollowButtonState();

    if (followDroneEnabled && lastTelemetry && shouldFollowTelemetry(lastTelemetry)) {
        moveCameraToDrone(lastTelemetry.latitude, lastTelemetry.longitude, true);
    }
}

function shouldFollowTelemetry(telemetry) {
    if (!followDroneEnabled || !telemetry) return false;
    if (currentFollowSessionId && telemetry.sessionId && +telemetry.sessionId !== +currentFollowSessionId) {
        return false;
    }
    return Number.isFinite(+telemetry.latitude) && Number.isFinite(+telemetry.longitude);
}

function withProgrammaticMonitorMove(callback, durationMs = 900) {
    monitorProgrammaticMove = true;
    clearTimeout(monitorProgrammaticMoveTimer);
    try {
        callback();
    } finally {
        monitorProgrammaticMoveTimer = setTimeout(() => {
            monitorProgrammaticMove = false;
        }, durationMs);
    }
}

function moveCameraToDrone(latitude, longitude, immediate = false) {
    if (!monitorMap) return;
    const lat = +latitude;
    const lon = +longitude;
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) return;

    const now = Date.now();
    if (!immediate && now - lastCameraMoveTime < FOLLOW_CAMERA_MIN_INTERVAL_MS) {
        return;
    }
    lastCameraMoveTime = now;

    withProgrammaticMonitorMove(() => {
        monitorMap.panTo([lat, lon], {
            animate: true,
            duration: immediate ? 0.35 : 0.65
        });
    });
}

function initMissionMap() {
    if (!missionMap) {
        missionMap = L.map('missionMap', { attributionControl: false }).setView(HOME, 13);
        createTileLayer().addTo(missionMap);
        missionWeatherOverlay = createWeatherMapOverlay(missionMap, 'mission');
        missionMap.on('click', e => {
            addWaypointRow({ latitude: e.latlng.lat, longitude: e.latlng.lng });
        });
    } else {
        missionMap.invalidateSize();
    }
    syncMissionMapFromForm();
}

function initDronePickMap() {
    if (!dronePickMap) {
        dronePickMap = L.map('dronePickMap', { zoomControl: true, attributionControl: false }).setView(HOME, 12);
        createTileLayer().addTo(dronePickMap);
        dronePickMap.on('click', e => {
            document.getElementById('droneLat').value = e.latlng.lat.toFixed(6);
            document.getElementById('droneLon').value = e.latlng.lng.toFixed(6);
            setDronePickMarker(e.latlng.lat, e.latlng.lng);
        });
    } else {
        dronePickMap.invalidateSize();
    }
    const lat = +document.getElementById('droneLat').value || HOME[0];
    const lon = +document.getElementById('droneLon').value || HOME[1];
    setDronePickMarker(lat, lon);
    dronePickMap.setView([lat, lon], 12);
}

function setDronePickMarker(lat, lon) {
    if (!dronePickMap) return;
    if (dronePickMarker) dronePickMap.removeLayer(dronePickMarker);
    dronePickMarker = L.marker([lat, lon]).addTo(dronePickMap);
}

function initChartsIfNeeded() {
    if (altitudeChart) return;
    if (typeof Chart === 'undefined') {
        console.error('Chart.js не загружен');
        return;
    }
    const opts = {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        plugins: { legend: { display: false } },
        scales: {
            x: { display: false },
            y: {
                ticks: { color: '#8fa3b8' },
                grid: { color: '#2a3748' }
            }
        }
    };
    altitudeChart = new Chart(document.getElementById('altitudeChart'), {
        type: 'line',
        data: {
            labels: chartData.labels.slice(),
            datasets: [{
                data: chartData.altitude.slice(),
                borderColor: '#3b82f6',
                backgroundColor: 'rgba(59, 130, 246, 0.1)',
                tension: 0.3,
                fill: true,
                pointRadius: 0
            }]
        },
        options: {
            ...opts,
            plugins: {
                ...opts.plugins,
                title: { display: true, text: 'Высота (м)', color: '#8fa3b8', font: { size: 12 } }
            }
        }
    });
    speedChart = new Chart(document.getElementById('speedChart'), {
        type: 'line',
        data: {
            labels: chartData.labels.slice(),
            datasets: [{
                data: chartData.speed.slice(),
                borderColor: '#22c55e',
                backgroundColor: 'rgba(34, 197, 94, 0.1)',
                tension: 0.3,
                fill: true,
                pointRadius: 0
            }]
        },
        options: {
            ...opts,
            plugins: {
                ...opts.plugins,
                title: { display: true, text: 'Скорость (м/с)', color: '#8fa3b8', font: { size: 12 } }
            }
        }
    });
}

async function restoreMonitorView() {
    const sessionId = activeSessionId || selectedSessionId;
    if (sessionId) {
        try {
            const session = await api('/sessions/' + sessionId);
            drawRoute(session);
        } catch (e) { /* ignore */ }
    }
    if (lastTelemetry) {
        updateMapFromTelemetry(lastTelemetry);
    }
}

function telemetryWeather(t) {
    if (t && t.weatherCode) {
        return {
            code: t.weatherCode,
            name: t.weatherName || WEATHER_TYPES[t.weatherCode]?.name || 'Погода',
            speedMultiplier: Number.isFinite(+t.weatherSpeedMultiplier) ? +t.weatherSpeedMultiplier : 1,
            batteryDrainMultiplier: Number.isFinite(+t.weatherBatteryMultiplier) ? +t.weatherBatteryMultiplier : 1,
            windSpeedMs: Number.isFinite(+t.windSpeedMs) ? +t.windSpeedMs : 0,
            precipitationMmH: Number.isFinite(+t.precipitationMmH) ? +t.precipitationMmH : 0,
            visibilityKm: Number.isFinite(+t.visibilityKm) ? +t.visibilityKm : 10
        };
    }
    return getWeatherAt(t.latitude, t.longitude);
}

function updateMapFromTelemetry(t) {
    if (!monitorMap) return;
    if (!droneMarker) {
        droneMarker = L.marker([t.latitude, t.longitude], {
            icon: L.divIcon({
                className: 'drone-marker-icon',
                html: '<div class="drone-dot"></div>',
                iconSize: [16, 16],
                iconAnchor: [8, 8]
            })
        }).addTo(monitorMap).bindPopup(t.callsign);
    } else {
        droneMarker.setLatLng([t.latitude, t.longitude]);
    }
    const weather = telemetryWeather(t);
    droneMarker.setPopupContent(`
        <strong>${escapeHtml(t.callsign)}</strong><br>
        Погода: ${escapeHtml(weather.name)}<br>
        Скорость: ${Math.round(weather.speedMultiplier * 100)}%<br>
        Батарея: ×${weather.batteryDrainMultiplier.toFixed(2)}
    `);
    if (shouldFollowTelemetry(t)) {
        moveCameraToDrone(t.latitude, t.longitude);
    }
}

function connectWebSocket() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = () => {};
    stompClient.connect({}, () => {
        setConnectionStatus(true);
        stompClient.subscribe('/topic/telemetry', msg => {
            const data = JSON.parse(msg.body);
            onTelemetry(data);
        });
    }, () => setConnectionStatus(false));
}

function setConnectionStatus(online) {
    const el = document.getElementById('connectionStatus');
    el.textContent = online ? 'Подключено (WebSocket)' : 'Отключено';
    el.className = 'status ' + (online ? 'online' : 'offline');
}

function onTelemetry(t) {
    lastTelemetry = t;
    activeSessionId = t.sessionId;
    if (followDroneEnabled && !currentFollowSessionId && t.sessionId) {
        currentFollowSessionId = t.sessionId;
    }
    document.getElementById('telAlt').textContent = t.altitudeM.toFixed(1) + ' м';
    document.getElementById('telSpeed').textContent = t.speedMs.toFixed(1) + ' м/с';
    document.getElementById('telHeading').textContent = t.headingDeg.toFixed(0) + '°';
    document.getElementById('telBattery').textContent = t.batteryPercent.toFixed(1) + '%';
    document.getElementById('telRoll').textContent = t.rollDeg.toFixed(1) + '°';
    document.getElementById('telPitch').textContent = t.pitchDeg.toFixed(1) + '°';
    document.getElementById('telProgress').textContent = t.progressPercent.toFixed(0) + '%';
    document.getElementById('telWaypoint').textContent = t.totalWaypoints > 0
        ? (t.waypointIndex + 1) + ' / ' + t.totalWaypoints
        : (t.sessionId ? '—' : 'Реальный БПЛА');
    const weather = telemetryWeather(t);
    document.getElementById('telWeather').textContent = weather.name;
    document.getElementById('telWeatherEffect').textContent = `${Math.round(weather.speedMultiplier * 100)}% скорости · батарея ×${weather.batteryDrainMultiplier.toFixed(2)}`;

    if (document.getElementById('view-monitor').classList.contains('active')) {
        if (!monitorMap) openMonitorView();
        else updateMapFromTelemetry(t);
    }

    pushChart(t.altitudeM, t.speedMs);
    updateSessionControls(t.sessionStatus);
}

function pushChart(alt, speed) {
    const max = 40;
    chartData.labels.push('');
    chartData.altitude.push(alt);
    chartData.speed.push(speed);
    if (chartData.labels.length > max) {
        chartData.labels.shift();
        chartData.altitude.shift();
        chartData.speed.shift();
    }
    if (!altitudeChart) {
        if (document.getElementById('view-monitor').classList.contains('active')) {
            initChartsIfNeeded();
        } else {
            return;
        }
    }
    if (!altitudeChart) return;
    altitudeChart.data.labels = chartData.labels;
    altitudeChart.data.datasets[0].data = chartData.altitude;
    altitudeChart.update('none');
    speedChart.data.labels = chartData.labels;
    speedChart.data.datasets[0].data = chartData.speed;
    speedChart.update('none');
}

async function loadDashboard() {
    const [stats, alerts, sessions] = await Promise.all([
        api('/dashboard'),
        api('/alerts'),
        api('/sessions')
    ]);

    document.getElementById('statsGrid').innerHTML = `
        <div class="stat-card"><span>БПЛА в системе</span><strong>${stats.totalDrones}</strong></div>
        <div class="stat-card"><span>В полёте</span><strong>${stats.activeDrones}</strong></div>
        <div class="stat-card"><span>Сессий</span><strong>${stats.totalSessions}</strong></div>
        <div class="stat-card"><span>Активных</span><strong>${stats.runningSessions}</strong></div>
        <div class="stat-card"><span>Оповещений</span><strong>${stats.unacknowledgedAlerts}</strong></div>
    `;

    activeSessionId = stats.activeSessionId;

    const active = sessions.find(s => s.id === stats.activeSessionId);
    updateSessionControls(active ? active.status : (selectedSessionId ? getSelectedSessionStatus(sessions) : null));
    const info = document.getElementById('activeSessionInfo');
    if (active) {
        info.innerHTML = `
            <p><strong>${active.name}</strong> — ${active.drone.callsign}</p>
            <p style="margin-top:8px;color:var(--muted)">Статус: <span class="badge ${active.status}">${active.status}</span></p>
            <p style="margin-top:4px;color:var(--muted)">Точек маршрута: ${active.waypoints.length}</p>
            ${formatSessionEstimateLine(active)}
        `;
        selectedSessionId = active.id;
        drawRoute(active);
    } else {
        info.textContent = 'Нет активной симуляции';
    }

    renderAlerts(document.getElementById('recentAlerts'), alerts.slice(0, 5));
}

function getSelectedSessionStatus(sessions) {
    const s = sessions.find(x => x.id === selectedSessionId);
    return s ? s.status : null;
}

function updateSessionControls(status) {
    const controls = document.getElementById('sessionControls');
    const running = status === 'RUNNING';
    const paused = status === 'PAUSED';
    const terminal = status === 'COMPLETED' || status === 'ABORTED';
    const hasTarget = !!(selectedSessionId || activeSessionId);
    const allowed = canModify();

    controls.hidden = !hasTarget || !allowed;
    document.getElementById('btnStart').disabled = !allowed || running || terminal || !status;
    document.getElementById('btnPause').disabled = !allowed || !running;
    document.getElementById('btnStop').disabled = !allowed || (!running && !paused);
    document.getElementById('btnEmergency').disabled = !allowed || (!running && !paused);
}

async function loadDrones() {
    const drones = await api('/drones');
    const tbody = document.querySelector('#dronesTable tbody');
    tbody.innerHTML = drones.map(d => {
        const sourceLabel = d.sourceType === 'EXTERNAL' ? 'Реальный' : 'Симулятор';
        const sourceClass = d.sourceType === 'EXTERNAL' ? 'EXTERNAL' : 'INFO';
        const conn = d.connected
            ? `<span class="badge FLYING">Подключён</span>`
            : `<span class="badge IDLE">—</span>`;
        const connectBtn = !canModify()
            ? '<span class="map-hint inline">только просмотр</span>'
            : (d.connected
                ? `<button class="btn small" onclick="disconnectDrone(${d.id})">Отключить</button>`
                : `<button class="btn small primary" onclick="openConnectModal(${d.id}, '${escapeAttr(d.callsign)}')">Подключить</button>`);
        const deleteBtn = canModify()
            ? `<button class="btn small danger" onclick="deleteDrone(${d.id})">Удалить</button>`
            : '';
        return `
        <tr>
            <td><strong>${escapeHtml(d.callsign)}</strong></td>
            <td>${escapeHtml(d.model)}</td>
            <td><span class="badge ${sourceClass}">${sourceLabel}</span> ${conn}</td>
            <td><span class="badge ${d.status}">${d.status}</span></td>
            <td>${d.batteryPercent.toFixed(0)}%</td>
            <td>${d.latitude.toFixed(5)}, ${d.longitude.toFixed(5)}</td>
            <td class="actions-cell">${connectBtn}${deleteBtn}</td>
        </tr>`;
    }).join('');
}

function escapeAttr(text) {
    return String(text ?? '').replace(/'/g, "\\'").replace(/"/g, '&quot;');
}

function openConnectModal(id, callsign) {
    document.getElementById('connectDroneId').value = id;
    document.getElementById('connectModalTitle').textContent = 'Подключение: ' + callsign;
    document.getElementById('connectModal').showModal();
}

async function submitConnect(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    const id = +fd.get('droneId');
    const body = {
        protocol: fd.get('protocol'),
        endpoint: fd.get('endpoint'),
        apiKey: fd.get('apiKey') || null,
        externalDeviceId: fd.get('externalDeviceId') || null,
        mqttTopic: fd.get('mqttTopic') || null
    };
    try {
        await api('/drones/' + id + '/connect', { method: 'POST', body: JSON.stringify(body) });
        document.getElementById('connectModal').close();
        loadDrones();
        alert('БПЛА подключён. Передавайте телеметрию по API (см. docs/REAL_DRONE.md).');
    } catch (err) {
        alert(err.message);
    }
}

async function disconnectDrone(id) {
    if (!confirm('Отключить реальный БПЛА?')) return;
    await api('/drones/' + id + '/disconnect', { method: 'POST' });
    loadDrones();
}

async function deleteDrone(id) {
    if (!confirm('Удалить БПЛА?')) return;
    await api('/drones/' + id, { method: 'DELETE' });
    loadDrones();
}

async function createDrone(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    const body = Object.fromEntries(fd.entries());
    body.maxSpeedMs = +body.maxSpeedMs;
    body.maxAltitudeM = +body.maxAltitudeM;
    body.batteryCapacityMah = +body.batteryCapacityMah;
    body.latitude = +body.latitude;
    body.longitude = +body.longitude;
    await api('/drones', { method: 'POST', body: JSON.stringify(body) });
    document.getElementById('droneModal').close();
    e.target.reset();
    loadDrones();
}

function addWaypointRow(data = {}) {
    const div = document.createElement('div');
    div.className = 'waypoint-row';
    const lat = (data.latitude ?? (HOME[0] + (Math.random() - 0.5) * 0.02)).toFixed(6);
    const lon = (data.longitude ?? (HOME[1] + (Math.random() - 0.5) * 0.02)).toFixed(6);
    div.innerHTML = `
        <input name="lat" type="number" step="0.000001" value="${lat}" placeholder="Широта" required title="Широта"/>
        <input name="lon" type="number" step="0.000001" value="${lon}" placeholder="Долгота" required title="Долгота"/>
        <input name="alt" type="number" value="${data.altitudeM ?? 120}" placeholder="Высота" required title="Высота, м"/>
        <input name="spd" type="number" step="0.1" value="${data.speedMs ?? formatSpeedInput(getSelectedDroneMaxSpeed() ?? 15)}" placeholder="Скор." required title="Скорость, м/с"/>
        <button type="button" class="btn small danger" title="Удалить точку">×</button>
    `;
    div.querySelector('button').onclick = () => {
        div.remove();
        syncMissionMapFromForm();
    };
    div.querySelectorAll('input').forEach(inp => {
        inp.addEventListener('input', syncMissionMapFromForm);
        inp.addEventListener('change', syncMissionMapFromForm);
    });
    document.getElementById('waypointsList').appendChild(div);
    syncMissionMapFromForm();
}

function clearWaypoints() {
    document.getElementById('waypointsList').innerHTML = '';
    syncMissionMapFromForm();
    updateMissionEstimate();
}

function syncMissionMapFromForm() {
    updateMissionEstimate();
    if (!missionMap) return;

    missionMarkers.forEach(m => missionMap.removeLayer(m));
    missionMarkers = [];
    if (missionStartMarker) {
        missionMap.removeLayer(missionStartMarker);
        missionStartMarker = null;
    }
    if (missionRouteLayer) {
        missionMap.removeLayer(missionRouteLayer);
        missionRouteLayer = null;
    }

    const drone = getSelectedDrone();
    const waypoints = readWaypointRows();
    const routePoints = buildRoutePoints(drone, waypoints);

    if (drone && Number.isFinite(+drone.latitude) && Number.isFinite(+drone.longitude) && waypoints.length > 0) {
        missionStartMarker = L.circleMarker([+drone.latitude, +drone.longitude], {
            radius: 9,
            color: '#ffffff',
            fillColor: '#111827',
            fillOpacity: 1,
            weight: 2
        }).addTo(missionMap).bindPopup(`Старт БПЛА ${escapeHtml(drone.callsign || '')}<br>Погода: ${weatherLabel(getWeatherAt(+drone.latitude, +drone.longitude))}`);
    }

    waypoints.forEach((point, i) => {
        if (!isValidPoint(point)) return;
        const weather = getWeatherAt(point.latitude, point.longitude);
        const marker = L.circleMarker([point.latitude, point.longitude], {
            radius: 8,
            color: weather.color,
            fillColor: weather.color,
            fillOpacity: 0.9
        }).addTo(missionMap).bindPopup(`Точка ${i + 1}<br>Погода в точке: ${weatherLabel(weather)}`);
        missionMarkers.push(marker);
    });

    if (routePoints.length >= 2) {
        missionRouteLayer = drawWeatherAwareRoute(missionMap, routePoints, { weight: 5 });
        const bounds = routeBounds(routePoints);
        if (bounds) missionMap.fitBounds(bounds, { padding: [30, 30] });
    } else if (routePoints.length === 1) {
        missionMap.setView([routePoints[0].latitude, routePoints[0].longitude], 14);
    }
}

let cachedSessions = [];

function renderSessionsList(sessions) {
    const list = document.getElementById('sessionsList');
    if (!Array.isArray(sessions) || sessions.length === 0) {
        list.innerHTML = '<li class="empty-state">Нет сессий</li>';
        return;
    }
    list.innerHTML = sessions.map(s => {
        const callsign = s.drone?.callsign ?? '—';
        const wpCount = s.waypoints?.length ?? 0;
        const created = s.createdAt ? new Date(s.createdAt).toLocaleString('ru') : '';
        const estimate = estimateFlightForSession(s);
        const estimateText = estimate
            ? ` · ${formatDistance(estimate.totalDistanceMeters)} · ~${formatDuration(estimate.durationSeconds)} · ${formatRouteWeather(estimate)}`
            : '';
        return `
        <li class="${s.id === selectedSessionId ? 'selected' : ''}" data-id="${s.id}">
            <div class="session-list-row">
                <div class="session-list-main">
                    <div><strong>${escapeHtml(s.name)}</strong> <span class="badge ${s.status}">${s.status}</span></div>
                    <small>${escapeHtml(callsign)} · ${wpCount} точек${estimateText} · ${created}</small>
                </div>
                <div class="session-list-actions">
                    <button type="button" class="btn small" data-show-id="${s.id}">Показать</button>
                    ${canModify() ? `<button type="button" class="btn small primary" data-repeat-id="${s.id}">Повторить маршрут</button>` : ''}
                </div>
            </div>
        </li>`;
    }).join('');

    list.querySelectorAll('li[data-id]').forEach(li => {
        li.addEventListener('click', async () => selectSessionFromHistory(+li.dataset.id));
    });
    list.querySelectorAll('[data-show-id]').forEach(btn => {
        btn.addEventListener('click', async event => {
            event.stopPropagation();
            await selectSessionFromHistory(+btn.dataset.showId);
        });
    });
    list.querySelectorAll('[data-repeat-id]').forEach(btn => {
        btn.addEventListener('click', async event => {
            event.stopPropagation();
            await repeatSessionFromHistory(+btn.dataset.repeatId);
        });
    });
}

async function selectSessionFromHistory(sessionId) {
    selectedSessionId = +sessionId;
    document.querySelectorAll('#sessionsList li[data-id]').forEach(x => {
        x.classList.toggle('selected', +x.dataset.id === selectedSessionId);
    });
    const session = await api('/sessions/' + selectedSessionId);
    drawRoute(session);
    updateSessionControls(session.status);
}

async function repeatSessionFromHistory(sessionId) {
    try {
        const source = cachedSessions.find(s => +s.id === +sessionId);
        const newSession = await api(`/sessions/${sessionId}/repeat`, { method: 'POST' });
        selectedSessionId = newSession.id;
        activeSessionId = null;
        await loadMissions();
        await loadDashboard();
        drawRoute(newSession);
        const estimate = estimateFlightForSession(newSession);
        showMissionNotice(estimate
            ? `Создан повтор маршрута${source ? ` «${source.name}»` : ''}: ${formatDistance(estimate.totalDistanceMeters)}, ~${formatDuration(estimate.durationSeconds)}. Новую миссию можно запускать.`
            : 'Создан повтор маршрута. Новую миссию можно запускать.');
    } catch (err) {
        showMissionNotice(err.message || 'Не удалось повторить маршрут', true);
        alert(err.message || 'Не удалось повторить маршрут');
    }
}


function getSelectedDrone() {
    const select = document.getElementById('missionDroneSelect');
    if (!select) return null;
    const id = +select.value;
    return cachedDrones.find(d => d.id === id) || null;
}

function getSelectedDroneMaxSpeed() {
    const drone = getSelectedDrone();
    return drone && Number.isFinite(+drone.maxSpeedMs) ? +drone.maxSpeedMs : null;
}

function setWaypointSpeedsFromSelectedDrone(force = false) {
    const maxSpeed = getSelectedDroneMaxSpeed();
    if (!maxSpeed) return;
    document.querySelectorAll('.waypoint-row').forEach(row => {
        const speedInput = row.querySelector('input[name="spd"]');
        if (!speedInput) return;
        const currentValue = +speedInput.value;
        if (force || !Number.isFinite(currentValue) || currentValue <= 0) {
            speedInput.value = formatSpeedInput(maxSpeed);
        }
    });
}

function readWaypointRows() {
    return [...document.querySelectorAll('.waypoint-row')].map(row => {
        const [lat, lon, alt, spd] = row.querySelectorAll('input');
        return {
            latitude: +lat.value,
            longitude: +lon.value,
            altitudeM: +alt.value,
            speedMs: +spd.value
        };
    }).filter(w => Number.isFinite(w.latitude) && Number.isFinite(w.longitude));
}

function updateMissionEstimate() {
    const distanceEl = document.getElementById('estimateDistance');
    const durationEl = document.getElementById('estimateDuration');
    const speedEl = document.getElementById('estimateSpeed');
    const weatherEl = document.getElementById('estimateWeather');
    if (!distanceEl || !durationEl || !speedEl || !weatherEl) return;

    const drone = getSelectedDrone();
    const waypoints = readWaypointRows();
    if (!drone || waypoints.length === 0) {
        distanceEl.textContent = '—';
        durationEl.textContent = '—';
        speedEl.textContent = '—';
        weatherEl.textContent = '—';
        weatherEl.className = '';
        renderRouteWeatherDetails(null);
        return;
    }

    const estimate = estimateFlight(drone, waypoints);
    distanceEl.textContent = formatDistance(estimate.totalDistanceMeters);
    durationEl.textContent = '~' + formatDuration(estimate.durationSeconds);
    speedEl.textContent = formatSpeed(drone.maxSpeedMs);
    weatherEl.textContent = formatRouteWeather(estimate);
    weatherEl.className = estimate.maxWeatherRisk >= 3 ? 'weather-critical'
        : estimate.maxWeatherRisk >= 2 ? 'weather-warning' : '';
    renderRouteWeatherDetails(estimate);
}

function estimateFlightForSession(session) {
    if (!session?.drone || !Array.isArray(session.waypoints) || session.waypoints.length === 0) {
        return null;
    }
    return estimateFlight(session.drone, session.waypoints);
}

function estimateFlight(drone, waypoints) {
    const maxSpeed = Math.max(0.1, +drone.maxSpeedMs || 0.1);
    let totalDistanceMeters = 0;
    let durationSeconds = 0;
    let weightedBatteryMultiplier = 0;
    let maxWeatherRisk = 0;
    let worstWeather = WEATHER_TYPES.CLEAR;
    let currentSpeedMs = 0;
    const weatherStats = new Map();

    const routePoints = buildRoutePoints(drone, waypoints);
    if (routePoints.length < 2) {
        return {
            totalDistanceMeters: 0,
            durationSeconds: 0,
            maxWeatherRisk: 0,
            worstWeather: WEATHER_TYPES.CLEAR,
            averageBatteryMultiplier: 1,
            weatherBreakdown: [{ code: 'CLEAR', distanceMeters: 0, durationSeconds: 0 }]
        };
    }

    for (let i = 1; i < routePoints.length; i++) {
        const startPoint = routePoints[i - 1];
        const endPoint = routePoints[i];
        const waypoint = waypoints[Math.max(0, i - 1)] || {};
        const requestedSpeed = Number.isFinite(+waypoint.speedMs) && +waypoint.speedMs > 0
            ? +waypoint.speedMs
            : maxSpeed;

        const samples = sampleWeatherAlongSegment(startPoint, endPoint);
        for (const sample of samples) {
            const weather = sample.weather;
            const targetSpeedMs = Math.max(0.1, Math.min(requestedSpeed, maxSpeed) * weather.speedMultiplier);
            const acceleration = accelerationForDrone(maxSpeed) * weather.accelerationMultiplier;
            const sampleDuration = estimateSegmentDuration(
                sample.distanceMeters,
                currentSpeedMs,
                targetSpeedMs,
                acceleration
            );

            totalDistanceMeters += sample.distanceMeters;
            durationSeconds += sampleDuration;
            weightedBatteryMultiplier += sampleDuration * weather.batteryDrainMultiplier;

            const stat = weatherStats.get(weather.code) || { code: weather.code, distanceMeters: 0, durationSeconds: 0 };
            stat.distanceMeters += sample.distanceMeters;
            stat.durationSeconds += sampleDuration;
            weatherStats.set(weather.code, stat);

            if (weather.risk > maxWeatherRisk) {
                maxWeatherRisk = weather.risk;
                worstWeather = weather;
            }
            currentSpeedMs = targetSpeedMs;
        }
    }

    const averageBatteryMultiplier = durationSeconds > 0
        ? weightedBatteryMultiplier / durationSeconds
        : 1;

    const weatherBreakdown = [...weatherStats.values()].sort((a, b) => {
        const riskA = WEATHER_TYPES[a.code]?.risk ?? 0;
        const riskB = WEATHER_TYPES[b.code]?.risk ?? 0;
        if (riskA !== riskB) return riskB - riskA;
        return b.distanceMeters - a.distanceMeters;
    });

    return {
        totalDistanceMeters,
        durationSeconds,
        maxWeatherRisk,
        worstWeather,
        averageBatteryMultiplier,
        weatherBreakdown
    };
}

function estimateSegmentDuration(distanceMetersValue, startSpeedMs, targetSpeedMs, accelerationMs2) {
    if (distanceMetersValue <= 0) return 0;
    if (accelerationMs2 <= 0) return distanceMetersValue / Math.max(0.1, targetSpeedMs);

    if (startSpeedMs < targetSpeedMs) {
        const accelerationTime = (targetSpeedMs - startSpeedMs) / accelerationMs2;
        const accelerationDistance = ((startSpeedMs + targetSpeedMs) / 2) * accelerationTime;
        if (accelerationDistance >= distanceMetersValue) {
            return solveAccelerationTime(distanceMetersValue, startSpeedMs, accelerationMs2);
        }
        return accelerationTime + (distanceMetersValue - accelerationDistance) / targetSpeedMs;
    }

    if (startSpeedMs > targetSpeedMs) {
        const decelerationTime = (startSpeedMs - targetSpeedMs) / accelerationMs2;
        const decelerationDistance = ((startSpeedMs + targetSpeedMs) / 2) * decelerationTime;
        if (decelerationDistance >= distanceMetersValue) {
            const discriminant = Math.max(0, startSpeedMs * startSpeedMs - 2 * accelerationMs2 * distanceMetersValue);
            return (startSpeedMs - Math.sqrt(discriminant)) / accelerationMs2;
        }
        return decelerationTime + (distanceMetersValue - decelerationDistance) / Math.max(0.1, targetSpeedMs);
    }

    return distanceMetersValue / Math.max(0.1, targetSpeedMs);
}

function solveAccelerationTime(distanceMetersValue, startSpeedMs, accelerationMs2) {
    const discriminant = startSpeedMs * startSpeedMs + 2 * accelerationMs2 * distanceMetersValue;
    return (-startSpeedMs + Math.sqrt(discriminant)) / accelerationMs2;
}

function accelerationForDrone(maxSpeedMs) {
    return Math.max(1.0, maxSpeedMs / 8.0);
}

function distanceMeters(lat1, lon1, lat2, lon2) {
    const earthRadiusMeters = 6371000;
    const dLat = toRadians(lat2 - lat1);
    const dLon = toRadians(lon2 - lon1);
    const startLat = toRadians(lat1);
    const endLat = toRadians(lat2);
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(startLat) * Math.cos(endLat)
        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const centralAngle = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return earthRadiusMeters * centralAngle;
}

function toRadians(degrees) {
    return degrees * Math.PI / 180;
}

function formatDistance(meters) {
    if (!Number.isFinite(meters)) return '—';
    if (meters < 1000) return Math.round(meters) + ' м';
    return (meters / 1000).toFixed(2) + ' км';
}

function formatDuration(seconds) {
    if (!Number.isFinite(seconds)) return '—';
    const rounded = Math.max(0, Math.round(seconds));
    const hours = Math.floor(rounded / 3600);
    const minutes = Math.floor((rounded % 3600) / 60);
    const secs = rounded % 60;
    if (hours > 0) return `${hours} ч ${minutes} мин`;
    if (minutes > 0) return `${minutes} мин ${secs} с`;
    return `${secs} с`;
}

function formatRouteWeather(estimate) {
    if (!estimate) return '—';
    const weather = estimate.worstWeather || WEATHER_TYPES.CLEAR;
    if (estimate.maxWeatherRisk === 0) {
        return `Ясно по маршруту · батарея ×${estimate.averageBatteryMultiplier.toFixed(2)}`;
    }
    return `${weather.name} по маршруту · скорость до ${Math.round(weather.speedMultiplier * 100)}% · батарея ×${estimate.averageBatteryMultiplier.toFixed(2)}`;
}

function formatSpeed(speedMs) {
    if (!Number.isFinite(+speedMs)) return '—';
    return (+speedMs).toFixed(1).replace('.0', '') + ' м/с';
}

function formatSpeedInput(speedMs) {
    return (+speedMs).toFixed(1).replace('.0', '');
}

function formatSessionEstimateLine(session) {
    const estimate = estimateFlightForSession(session);
    if (!estimate) return '';
    return `<p style="margin-top:4px;color:var(--muted)">Маршрут: ${formatDistance(estimate.totalDistanceMeters)}, примерно ${formatDuration(estimate.durationSeconds)}, ${formatRouteWeather(estimate)}</p>`;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text ?? '';
    return div.innerHTML;
}

function renderDroneSelect(drones) {
    const select = document.getElementById('missionDroneSelect');
    const previousValue = select.value;
    const simDrones = drones.filter(d => d.sourceType !== 'EXTERNAL');
    select.innerHTML = simDrones.map(d =>
        `<option value="${d.id}">${escapeHtml(d.callsign)} (${escapeHtml(d.model)}) — ${d.status}, макс. ${formatSpeed(d.maxSpeedMs)}</option>`
    ).join('') || '<option value="">Нет симуляторных БПЛА</option>';
    if (previousValue && [...select.options].some(option => option.value === previousValue)) {
        select.value = previousValue;
    }
}

function addDefaultWaypointRows() {
    addWaypointRow({ latitude: 55.755, longitude: 37.625, altitudeM: 150 });
    addWaypointRow({ latitude: 55.748, longitude: 37.640, altitudeM: 200 });
    addWaypointRow({ latitude: 55.760, longitude: 37.610, altitudeM: 100 });
}

function resetMissionForm() {
    document.getElementById('missionForm').reset();
    document.getElementById('waypointsList').innerHTML = '';
    addDefaultWaypointRows();
    syncMissionMapFromForm();
}

function showMissionNotice(message, isError = false) {
    let el = document.getElementById('missionNotice');
    if (!el) {
        el = document.createElement('p');
        el.id = 'missionNotice';
        el.className = 'map-hint';
        document.getElementById('missionForm').prepend(el);
    }
    el.textContent = message;
    el.style.color = isError ? 'var(--danger)' : 'var(--success)';
}

async function loadMissions() {
    const [drones, sessions] = await Promise.all([api('/drones'), api('/sessions')]);
    cachedDrones = drones;
    cachedSessions = sessions;
    renderDroneSelect(drones);
    renderSessionsList(sessions);

    if (document.getElementById('waypointsList').children.length === 0) {
        addDefaultWaypointRows();
    }
    setWaypointSpeedsFromSelectedDrone(false);
    updateMissionEstimate();
}

async function createMission(e) {
    e.preventDefault();
    const submitBtn = e.target.querySelector('button[type="submit"]');
    submitBtn.disabled = true;
    showMissionNotice('Создание сессии…');

    try {
        const fd = new FormData(e.target);
        const waypoints = readWaypointRows();

        if (waypoints.length === 0) {
            throw new Error('Добавьте хотя бы одну точку маршрута');
        }

        const body = {
            name: fd.get('name'),
            description: fd.get('description'),
            droneId: +fd.get('droneId'),
            waypoints
        };
        const session = await api('/sessions', { method: 'POST', body: JSON.stringify(body) });
        selectedSessionId = session.id;

        cachedSessions = [session, ...cachedSessions.filter(s => s.id !== session.id)];
        renderSessionsList(cachedSessions);

        const estimate = estimateFlightForSession(session);
        resetMissionForm();
        showMissionNotice(estimate
            ? `Сессия «${session.name}» создана. Маршрут ${formatDistance(estimate.totalDistanceMeters)}, время ~${formatDuration(estimate.durationSeconds)}, ${formatRouteWeather(estimate)}.`
            : `Сессия «${session.name}» создана. Нажмите «Старт» на панели.`);

        try {
            const sessions = await api('/sessions');
            cachedSessions = sessions;
            renderSessionsList(sessions);
        } catch (reloadErr) {
            console.warn('Не удалось обновить список с сервера', reloadErr);
        }
    } catch (err) {
        showMissionNotice(err.message || 'Ошибка создания сессии', true);
    } finally {
        submitBtn.disabled = false;
    }
}

async function controlSession(action, emergency = false) {
    const id = selectedSessionId || activeSessionId;
    if (!id) return alert('Выберите сессию в списке миссий или запустите сессию с панели');

    let path = `/sessions/${id}/${action}`;
    if (action === 'stop') {
        path += '?emergency=' + emergency;
    }

    try {
        const session = await api(path, { method: 'POST' });
        selectedSessionId = session.id;
        activeSessionId = (session.status === 'RUNNING' || session.status === 'PAUSED')
            ? session.id
            : null;
        updateSessionControls(session.status);

        if (action === 'start') {
            chartData.labels.length = 0;
            chartData.altitude.length = 0;
            chartData.speed.length = 0;
            lastTelemetry = null;
            currentFollowSessionId = session.id;
            setFollowDrone(true, session.id);
        }
        if (action === 'stop' || emergency) {
            activeSessionId = null;
            if (currentFollowSessionId === session.id) {
                setFollowDrone(false);
            }
            updateSessionControls(session.status);
        }

        await loadDashboard();
        await loadMissions();
        if (action === 'start') {
            switchView('monitor');
            drawRoute(session);
        } else if (document.getElementById('view-monitor').classList.contains('active')) {
            openMonitorView();
        }
    } catch (err) {
        alert(err.message || 'Ошибка управления сессией');
    }
}

function clearMonitorRoute() {
    if (!monitorMap) return;
    if (routeLayer) {
        monitorMap.removeLayer(routeLayer);
        routeLayer = null;
    }
    waypointMarkers.forEach(m => monitorMap.removeLayer(m));
    waypointMarkers = [];
}

function clearMissionPreview() {
    if (!missionMap) return;
    if (missionPreviewRouteLayer) {
        missionMap.removeLayer(missionPreviewRouteLayer);
        missionPreviewRouteLayer = null;
    }
    if (missionPreviewStartMarker) {
        missionMap.removeLayer(missionPreviewStartMarker);
        missionPreviewStartMarker = null;
    }
    missionPreviewMarkers.forEach(m => missionMap.removeLayer(m));
    missionPreviewMarkers = [];
}

function drawRoute(session) {
    const onMissions = document.getElementById('view-missions').classList.contains('active');
    const drone = session.drone;
    const waypoints = session.waypoints || [];
    const routePoints = buildRoutePoints(drone, waypoints);
    if (routePoints.length < 2) return;

    if (onMissions) {
        if (!missionMap) return;
        clearMissionPreview();

        missionPreviewRouteLayer = drawWeatherAwareRoute(missionMap, routePoints, {
            weight: 5,
            opacity: 0.9,
            dashArray: '8 6'
        });

        if (drone && Number.isFinite(+drone.latitude) && Number.isFinite(+drone.longitude)) {
            missionPreviewStartMarker = L.circleMarker([+drone.latitude, +drone.longitude], {
                radius: 9,
                color: '#ffffff',
                fillColor: '#111827',
                fillOpacity: 1,
                weight: 2
            }).addTo(missionMap).bindPopup(`Старт БПЛА ${escapeHtml(drone.callsign || '')}<br>Погода: ${weatherLabel(getWeatherAt(+drone.latitude, +drone.longitude))}`);
        }

        waypoints.forEach((w, i) => {
            const weather = getWeatherAt(w.latitude, w.longitude);
            const m = L.circleMarker([w.latitude, w.longitude], {
                radius: 7,
                color: weather.color,
                fillColor: weather.color,
                fillOpacity: 0.85
            }).addTo(missionMap).bindPopup(`Маршрут: точка ${i + 1}, ${w.altitudeM} м, ${formatSpeed(w.speedMs)}<br>Погода в точке: ${weatherLabel(weather)}`);
            missionPreviewMarkers.push(m);
        });

        const bounds = routeBounds(routePoints);
        if (bounds) missionMap.fitBounds(bounds, { padding: [30, 30] });
        return;
    }

    if (!monitorMap) return;
    clearMonitorRoute();
    routeLayer = drawWeatherAwareRoute(monitorMap, routePoints, { weight: 4, opacity: 0.9, dashArray: '6 8' });

    if (drone && Number.isFinite(+drone.latitude) && Number.isFinite(+drone.longitude)) {
        const start = L.circleMarker([+drone.latitude, +drone.longitude], {
            radius: 8,
            color: '#ffffff',
            fillColor: '#111827',
            fillOpacity: 1,
            weight: 2
        }).addTo(monitorMap).bindPopup(`Старт БПЛА ${escapeHtml(drone.callsign || '')}<br>Погода: ${weatherLabel(getWeatherAt(+drone.latitude, +drone.longitude))}`);
        waypointMarkers.push(start);
    }

    waypoints.forEach((w, i) => {
        const weather = getWeatherAt(w.latitude, w.longitude);
        const m = L.circleMarker([w.latitude, w.longitude], {
            radius: 6,
            color: weather.color,
            fillColor: weather.color,
            fillOpacity: 0.8
        }).addTo(monitorMap).bindPopup(`Точка ${i + 1}: ${w.altitudeM} м, ${formatSpeed(w.speedMs)}<br>Погода в точке: ${weatherLabel(weather)}`);
        waypointMarkers.push(m);
    });

    const bounds = routeBounds(routePoints);
    if (bounds && !followDroneEnabled) {
        withProgrammaticMonitorMove(() => monitorMap.fitBounds(bounds, { padding: [40, 40] }));
    }
}

async function loadAlerts() {
    const alerts = await api('/alerts');
    renderAlerts(document.getElementById('alertsList'), alerts, true);
}

function renderAlerts(container, alerts, full = false) {
    container.innerHTML = alerts.map(a => `
        <li class="${a.acknowledged ? 'ack' : ''}">
            <div>
                <span class="badge ${a.level}">${a.level}</span>
                ${a.message}
                <div style="color:var(--muted);font-size:11px;margin-top:4px">${new Date(a.createdAt).toLocaleString('ru')}</div>
            </div>
            ${full && !a.acknowledged ? `<button class="btn small" onclick="ackAlert(${a.id})">OK</button>` : ''}
        </li>
    `).join('') || '<li class="empty-state">Нет оповещений</li>';
}

async function ackAlert(id) {
    await api('/alerts/' + id + '/acknowledge', { method: 'POST' });
    loadAlerts();
    loadDashboard();
}


function updateAuthUi() {
    const authUser = document.getElementById('authUser');
    const btnLogin = document.getElementById('btnLogin');
    const btnLogout = document.getElementById('btnLogout');
    const adminNav = document.querySelector('[data-view="admin"]');

    if (currentUser) {
        authUser.innerHTML = `<strong>${escapeHtml(currentUser.fullName || currentUser.username)}</strong><br><span>${roleLabel(currentUser.role)}</span>`;
        btnLogin.hidden = true;
        btnLogout.hidden = false;
    } else {
        authUser.textContent = 'Гость: только просмотр';
        btnLogin.hidden = false;
        btnLogout.hidden = true;
    }

    if (adminNav) {
        adminNav.hidden = !isAdmin();
    }

    document.getElementById('btnAddDrone').hidden = !canModify();
    document.querySelector('#missionForm button[type="submit"]').disabled = !canModify();
    document.getElementById('btnAddWaypoint').disabled = !canModify();
    document.getElementById('btnClearWaypoints').disabled = !canModify();
}

async function login(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    try {
        const user = await api('/auth/login', {
            method: 'POST',
            body: JSON.stringify({
                username: fd.get('username'),
                password: fd.get('password')
            })
        });
        saveCurrentUser(user);
        document.getElementById('loginModal').close();
        await refreshCurrentView();
    } catch (err) {
        alert(err.message || 'Не удалось войти');
    }
}

function logout() {
    saveCurrentUser(null);
    if (document.querySelector('#view-admin.active')) {
        switchView('dashboard');
    }
    refreshCurrentView();
}

async function refreshCurrentView() {
    const active = document.querySelector('.view.active');
    const viewName = Object.keys(views).find(key => views[key].el === active?.id) || 'dashboard';
    refreshView(viewName);
}

function roleLabel(role) {
    if (role === 'ADMIN') return 'Администратор';
    if (role === 'OPERATOR') return 'Оператор';
    if (role === 'OBSERVER') return 'Наблюдатель';
    return role || '—';
}

function formatDateTime(value) {
    return value ? new Date(value).toLocaleString('ru') : '—';
}

async function loadAdmin() {
    if (!isAdmin()) return;
    await Promise.all([loadAdminUsers(), loadAdminActions()]);
}

async function loadAdminUsers() {
    const users = await api('/admin/users');
    const tbody = document.querySelector('#adminUsersTable tbody');
    tbody.innerHTML = users.map(user => `
        <tr data-user-id="${user.id}">
            <td><strong>${escapeHtml(user.username)}</strong></td>
            <td><input class="admin-input" data-field="fullName" value="${escapeAttr(user.fullName)}"/></td>
            <td>
                <select class="admin-input" data-field="role">
                    <option value="ADMIN" ${user.role === 'ADMIN' ? 'selected' : ''}>ADMIN</option>
                    <option value="OPERATOR" ${user.role === 'OPERATOR' ? 'selected' : ''}>OPERATOR</option>
                    <option value="OBSERVER" ${user.role === 'OBSERVER' ? 'selected' : ''}>OBSERVER</option>
                </select>
            </td>
            <td><input data-field="active" type="checkbox" ${user.active ? 'checked' : ''}/></td>
            <td>${formatDateTime(user.lastLoginAt)}</td>
            <td>
                <input class="admin-input password-input" data-field="password" placeholder="новый пароль"/>
                <button type="button" class="btn small primary" data-save-user="${user.id}">Сохранить</button>
            </td>
        </tr>
    `).join('');

    tbody.querySelectorAll('[data-save-user]').forEach(btn => {
        btn.addEventListener('click', () => saveAdminUser(+btn.dataset.saveUser));
    });
}

async function createAdminUser(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    try {
        await api('/admin/users', {
            method: 'POST',
            body: JSON.stringify({
                username: fd.get('username'),
                fullName: fd.get('fullName'),
                password: fd.get('password'),
                role: fd.get('role'),
                active: fd.get('active') === 'on'
            })
        });
        e.target.reset();
        e.target.elements.active.checked = true;
        await loadAdminUsers();
        await loadAdminActions();
    } catch (err) {
        alert(err.message || 'Не удалось добавить пользователя');
    }
}

async function saveAdminUser(id) {
    const row = document.querySelector(`#adminUsersTable tr[data-user-id="${id}"]`);
    if (!row) return;

    const body = {
        fullName: row.querySelector('[data-field="fullName"]').value,
        role: row.querySelector('[data-field="role"]').value,
        active: row.querySelector('[data-field="active"]').checked,
        password: row.querySelector('[data-field="password"]').value || null
    };

    try {
        const updated = await api('/admin/users/' + id, { method: 'PUT', body: JSON.stringify(body) });
        if (currentUser?.id === updated.id) {
            saveCurrentUser(updated);
        }
        await loadAdminUsers();
        await loadAdminActions();
    } catch (err) {
        alert(err.message || 'Не удалось сохранить пользователя');
    }
}

async function loadAdminActions() {
    if (!isAdmin()) return;
    const actions = await api('/admin/actions?limit=120');
    const tbody = document.querySelector('#adminActionsTable tbody');
    tbody.innerHTML = actions.map(action => {
        const user = action.user ? `${escapeHtml(action.user.username)}<br><span class="muted-small">${roleLabel(action.user.role)}</span>` : '—';
        const statusClass = action.status >= 400 ? 'danger-text' : 'success-text';
        return `
        <tr data-action-id="${action.id}">
            <td>${formatDateTime(action.createdAt)}</td>
            <td>${user}</td>
            <td>${escapeHtml(action.actionType)}<br><span class="muted-small">${escapeHtml(action.method)} · ${escapeHtml(action.details || '')}</span></td>
            <td><code>${escapeHtml(action.path)}</code></td>
            <td class="${statusClass}">${action.status}</td>
            <td>
                <label class="checkbox-label small-check"><input data-field="reviewed" type="checkbox" ${action.reviewed ? 'checked' : ''}/> проверено</label>
                <input class="admin-input" data-field="adminComment" value="${escapeAttr(action.adminComment || '')}" placeholder="комментарий"/>
                <button type="button" class="btn small" data-review-action="${action.id}">OK</button>
            </td>
        </tr>`;
    }).join('') || '<tr><td colspan="6" class="empty-state">Действий пока нет</td></tr>';

    tbody.querySelectorAll('[data-review-action]').forEach(btn => {
        btn.addEventListener('click', () => reviewAdminAction(+btn.dataset.reviewAction));
    });
}

async function reviewAdminAction(id) {
    const row = document.querySelector(`#adminActionsTable tr[data-action-id="${id}"]`);
    if (!row) return;
    try {
        await api(`/admin/actions/${id}/review`, {
            method: 'PUT',
            body: JSON.stringify({
                reviewed: row.querySelector('[data-field="reviewed"]').checked,
                adminComment: row.querySelector('[data-field="adminComment"]').value
            })
        });
        await loadAdminActions();
    } catch (err) {
        alert(err.message || 'Не удалось обновить действие');
    }
}

updateAuthUi();
connectWebSocket();
loadDashboard();
setInterval(() => {
    const active = document.querySelector('.view.active');
    if (active?.id === 'view-dashboard') loadDashboard();
}, 5000);
