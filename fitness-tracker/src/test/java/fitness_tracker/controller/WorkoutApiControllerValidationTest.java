package fitness_tracker.controller;

import fitness_tracker.entity.User;
import fitness_tracker.service.CurrentUserService;
import fitness_tracker.service.WorkoutService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkoutApiController.class)
@WithMockUser(username = "test@example.com")
class WorkoutApiControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkoutService workoutService;

    @MockBean
    private CurrentUserService currentUserService;

    @Test
    void createReturnsBadRequestForInvalidWorkoutPayload() throws Exception {
        User user = new User();
        user.setEmail("test@example.com");
        given(currentUserService.getCurrentUser()).willReturn(user);

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
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("workoutDate")))
                .andExpect(content().string(containsString("bodyPart")))
                .andExpect(content().string(containsString("rpe")));
    }
}
