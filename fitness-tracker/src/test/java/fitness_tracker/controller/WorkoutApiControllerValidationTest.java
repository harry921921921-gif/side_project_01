package fitness_tracker.controller;

import fitness_tracker.service.WorkoutService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkoutApiController.class)
class WorkoutApiControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkoutService workoutService;

    @Test
    void createReturnsBadRequestForInvalidWorkoutPayload() throws Exception {
        String payload = """
                {
                  "workoutDate": "2100-01-01",
                  "bodyPart": "   ",
                  "note": "invalid",
                  "exercises": [
                    {
                      "exerciseName": "Bench Press",
                      "weightKg": 80.0,
                      "sets": 3,
                      "reps": 10,
                      "rpe": 20.0,
                      "completionStatus": "COMPLETE",
                      "actualReps": 10,
                      "actualWeight": 80.0,
                      "notes": "bad"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/workouts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("workoutDate")))
                .andExpect(content().string(containsString("bodyPart")))
                .andExpect(content().string(containsString("rpe")));
    }
}
