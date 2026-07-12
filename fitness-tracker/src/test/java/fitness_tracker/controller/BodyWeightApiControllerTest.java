package fitness_tracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import fitness_tracker.entity.BodyWeight;
import fitness_tracker.service.BodyWeightService;

@WebMvcTest(BodyWeightApiController.class)
class BodyWeightApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BodyWeightService bodyWeightService;

    @Test
    void listReturnsBodyWeightJson() throws Exception {
        BodyWeight record = newBodyWeight(1L, LocalDate.of(2026, 7, 10), 70.5);
        given(bodyWeightService.findPage(any())).willReturn(new PageImpl<>(List.of(record), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/body-weights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].weightKg").value(70.5));
    }

    @Test
    void latestReturnsNotFoundWhenNoRecords() throws Exception {
        given(bodyWeightService.findLatest()).willReturn(Optional.empty());

        mockMvc.perform(get("/api/body-weights/latest"))
                .andExpect(status().isNotFound());
    }

    @Test
    void latestReturnsOkWhenRecordExists() throws Exception {
        given(bodyWeightService.findLatest()).willReturn(Optional.of(newBodyWeight(1L, LocalDate.of(2026, 7, 10), 70.5)));

        mockMvc.perform(get("/api/body-weights/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightKg").value(70.5));
    }

    @Test
    void createReturnsBadRequestForFutureDate() throws Exception {
        String payload = """
                {
                  "recordedDate": "2100-01-01",
                  "weightKg": 70.0,
                  "timeOfDay": "MORNING",
                  "note": "test"
                }
                """;

        mockMvc.perform(post("/api/body-weights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void createReturnsBadRequestForNegativeWeight() throws Exception {
        String payload = """
                {
                  "recordedDate": "2026-07-10",
                  "weightKg": -5.0,
                  "timeOfDay": "MORNING",
                  "note": "test"
                }
                """;

        mockMvc.perform(post("/api/body-weights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void updateReturnsNotFoundForMissingRecord() throws Exception {
        given(bodyWeightService.findById(999L)).willReturn(Optional.empty());

        String payload = """
                {
                  "recordedDate": "2026-07-10",
                  "weightKg": 70.0,
                  "timeOfDay": "MORNING",
                  "note": "test"
                }
                """;

        mockMvc.perform(put("/api/body-weights/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturnsNotFoundForMissingRecord() throws Exception {
        given(bodyWeightService.findById(999L)).willReturn(Optional.empty());

        mockMvc.perform(delete("/api/body-weights/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturnsNoContentWhenRecordExists() throws Exception {
        given(bodyWeightService.findById(1L)).willReturn(Optional.of(newBodyWeight(1L, LocalDate.of(2026, 7, 10), 70.5)));

        mockMvc.perform(delete("/api/body-weights/1"))
                .andExpect(status().isNoContent());
    }

    private BodyWeight newBodyWeight(Long id, LocalDate date, double weightKg) {
        BodyWeight bodyWeight = new BodyWeight();
        bodyWeight.setId(id);
        bodyWeight.setRecordedDate(date);
        bodyWeight.setWeightKg(weightKg);
        return bodyWeight;
    }
}
