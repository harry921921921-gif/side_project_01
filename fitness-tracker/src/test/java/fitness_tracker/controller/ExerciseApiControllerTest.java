package fitness_tracker.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import fitness_tracker.entity.Exercise;
import fitness_tracker.service.ExerciseService;

@WebMvcTest(ExerciseApiController.class)
class ExerciseApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExerciseService exerciseService;

    @Test
    void listReturnsExerciseJson() throws Exception {
        given(exerciseService.findAll()).willReturn(List.of(newExercise(1L, "臥推", "胸", "COMPOUND")));

        mockMvc.perform(get("/api/exercises"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("臥推"));
    }

    @Test
    void listFiltersByBodyPart() throws Exception {
        given(exerciseService.findByBodyPart("背")).willReturn(List.of(newExercise(2L, "引體向上", "背", "COMPOUND")));

        mockMvc.perform(get("/api/exercises").param("bodyPart", "背"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bodyPart").value("背"));
    }

    @Test
    void createReturnsOkForValidPayload() throws Exception {
        given(exerciseService.addCustom("自訂動作", "胸", "ISOLATION"))
                .willReturn(Optional.of(newExercise(3L, "自訂動作", "胸", "ISOLATION")));

        String payload = """
                {
                  "name": "自訂動作",
                  "bodyPart": "胸",
                  "category": "ISOLATION"
                }
                """;

        mockMvc.perform(post("/api/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("自訂動作"));
    }

    @Test
    void createReturnsBadRequestForBlankName() throws Exception {
        String payload = """
                {
                  "name": "   ",
                  "bodyPart": "胸",
                  "category": "ISOLATION"
                }
                """;

        mockMvc.perform(post("/api/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void createReturnsConflictWhenExerciseAlreadyExists() throws Exception {
        given(exerciseService.addCustom("臥推", "胸", "COMPOUND")).willReturn(Optional.empty());

        String payload = """
                {
                  "name": "臥推",
                  "bodyPart": "胸",
                  "category": "COMPOUND"
                }
                """;

        mockMvc.perform(post("/api/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }

    private Exercise newExercise(Long id, String name, String bodyPart, String category) {
        Exercise exercise = new Exercise();
        exercise.setId(id);
        exercise.setName(name);
        exercise.setBodyPart(bodyPart);
        exercise.setCategory(category);
        exercise.setPreset(true);
        exercise.setOrderIndex(0);
        return exercise;
    }
}
