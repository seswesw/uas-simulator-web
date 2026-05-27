# Подключение реальных БПЛА

Система поддерживает мониторинг **реальных** дронов параллельно с симулятором.

## 1. Подключение в интерфейсе

1. Откройте **БПЛА** → **Подключить** у нужного аппарата.
2. Выберите протокол:
   - **HTTP** — наземная станция отправляет телеметрию REST-запросами;
   - **MQTT** — подписка на топик брокера (JSON);
   - **MAVLink** — используйте мост (например, mavlink-router), который переводит данные в HTTP POST.
3. Укажите endpoint и при необходимости API-ключ.

Реальные БПЛА **не участвуют в симуляции миссий** — только в мониторинге.

## 2. HTTP — передача телеметрии

```bash
curl -X POST "http://localhost:8080/api/drones/1/telemetry" \
  -H "Content-Type: application/json" \
  -H "X-Drone-Key: ваш-ключ" \
  -d "{
    \"latitude\": 55.752,
    \"longitude\": 37.616,
    \"altitudeM\": 120.5,
    \"speedMs\": 8.2,
    \"headingDeg\": 45,
    \"pitchDeg\": 2,
    \"rollDeg\": -1,
    \"batteryPercent\": 87.5
  }"
```

Данные появятся в **Мониторинге** (WebSocket `/topic/telemetry`) и обновят позицию на карте.

## 3. MQTT

Endpoint: `mqtt://localhost:1883` или `mqtt://user:pass@broker:1883`

Топик (пример): `uas/telemetry/1`

Сообщение (JSON):

```json
{
  "latitude": 55.752,
  "longitude": 37.616,
  "altitudeM": 120,
  "speedMs": 10,
  "headingDeg": 90,
  "pitchDeg": 0,
  "rollDeg": 0,
  "batteryPercent": 80
}
```

## 4. MAVLink

Прямой парсинг MAVLink в Java требует отдельного сервиса (DroneKit, mavlink-router).
Рекомендуемая схема:

```
Дрон → MAVLink → mavlink-router / GCS → скрипт-адаптер → POST /api/drones/{id}/telemetry
```

Пример адаптера можно реализовать на Python с библиотекой `pymavlink`.

## 5. Интеграция с GCS

| Система | Способ |
|---------|--------|
| QGroundControl / Mission Planner | Экспорт телеметрии через свой скрипт → HTTP API |
| ROS / MAVROS | Узел-ретранслятор → HTTP API |
| Свой наземный комплекс | Прямой POST или MQTT |

## 6. Безопасность (для продакшена)

- HTTPS;
- обязательный `X-Drone-Key`;
- whitelist IP;
- отдельная сеть VLAN для БПЛА.
