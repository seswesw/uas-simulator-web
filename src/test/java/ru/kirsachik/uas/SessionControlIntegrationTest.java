package ru.kirsachik.uas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.kirsachik.uas.repository.DroneRepository;
import ru.kirsachik.uas.repository.SimulationSessionRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class SessionControlIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DroneRepository droneRepository;

    @Autowired
    SimulationSessionRepository sessionRepository;

    @Test
    void pauseStopAndEmergencyWork() throws Exception {
        Long droneId = droneRepository.findAll().get(0).getId();

        String createBody = """
                {
                  "name": "Test-Control",
                  "droneId": %d,
                  "waypoints": [
                    {"latitude": 55.751, "longitude": 37.618, "altitudeM": 100, "speedMs": 15},
                    {"latitude": 55.755, "longitude": 37.625, "altitudeM": 120, "speedMs": 15}
                  ]
                }
                """.formatted(droneId);

        MvcResult created = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        String json = created.getResponse().getContentAsString();
        Long sessionId = extractId(json);

        mockMvc.perform(post("/api/sessions/" + sessionId + "/start"))
                .andExpect(status().isOk());
        assertEquals("RUNNING", sessionRepository.findById(sessionId).orElseThrow().getStatus().name());

        mockMvc.perform(post("/api/sessions/" + sessionId + "/pause"))
                .andExpect(status().isOk());
        assertEquals("PAUSED", sessionRepository.findById(sessionId).orElseThrow().getStatus().name());

        mockMvc.perform(post("/api/sessions/" + sessionId + "/start"))
                .andExpect(status().isOk());
        assertEquals("RUNNING", sessionRepository.findById(sessionId).orElseThrow().getStatus().name());

        mockMvc.perform(post("/api/sessions/" + sessionId + "/stop"))
                .andExpect(status().isOk());
        assertEquals("COMPLETED", sessionRepository.findById(sessionId).orElseThrow().getStatus().name());

        Long sessionId2 = createAnotherSession(droneId);
        mockMvc.perform(post("/api/sessions/" + sessionId2 + "/start"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/sessions/" + sessionId2 + "/stop?emergency=true"))
                .andExpect(status().isOk());
        assertEquals("ABORTED", sessionRepository.findById(sessionId2).orElseThrow().getStatus().name());
    }

    private Long createAnotherSession(Long droneId) throws Exception {
        String body = """
                {
                  "name": "Test-Emergency",
                  "droneId": %d,
                  "waypoints": [
                    {"latitude": 55.751, "longitude": 37.618, "altitudeM": 100, "speedMs": 15}
                  ]
                }
                """.formatted(droneId);
        MvcResult r = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(r.getResponse().getContentAsString());
    }

    private static Long extractId(String json) {
        int idx = json.indexOf("\"id\":");
        if (idx < 0) {
            throw new IllegalStateException("No id in response: " + json);
        }
        int start = idx + 5;
        int end = json.indexOf(',', start);
        if (end < 0) {
            end = json.indexOf('}', start);
        }
        return Long.parseLong(json.substring(start, end).trim());
    }
}
