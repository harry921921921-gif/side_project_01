package fitness_tracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import fitness_tracker.entity.User;
import fitness_tracker.entity.WorkoutSession;
import fitness_tracker.exception.ResourceNotFoundException;
import fitness_tracker.service.CurrentUserService;
import fitness_tracker.service.WorkoutService;

@WebMvcTest(WorkoutApiController.class)
@WithMockUser(username = "test@example.com")
class WorkoutApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkoutService workoutService;

    @MockBean
    private CurrentUserService currentUserService;

    @Test
    void listReturnsWorkoutJson() throws Exception {
        given(currentUserService.getCurrentUser()).willReturn(testUser());
        WorkoutSession session = createSession(1L, LocalDate.of(2026, 7, 10), "胸");
        given(workoutService.findPage(any(), any())).willReturn(new PageImpl<>(List.of(session), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/workouts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].bodyPart").value("胸"));
    }

    @Test
    void getMissingWorkoutReturnsNotFound() throws Exception {
        given(currentUserService.getCurrentUser()).willReturn(testUser());
        given(workoutService.findById(999L, testUser())).willReturn(Optional.empty());

        mockMvc.perform(get("/api/workouts/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void createReturnsBadRequestForInvalidPayload() throws Exception {
        given(currentUserService.getCurrentUser()).willReturn(testUser());
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
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void deleteReturnsNoContentWhenWorkoutExists() throws Exception {
        given(currentUserService.getCurrentUser()).willReturn(testUser());

        mockMvc.perform(delete("/api/workouts/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(workoutService).delete(eq(1L), any());
    }

    @Test
    void deleteReturnsNotFoundForMissingWorkout() throws Exception {
        given(currentUserService.getCurrentUser()).willReturn(testUser());
        doThrow(new ResourceNotFoundException("找不到 id=999 的訓練紀錄"))
                .when(workoutService).delete(eq(999L), any());

        mockMvc.perform(delete("/api/workouts/999").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    private WorkoutSession createSession(Long id, LocalDate date, String bodyPart) {
        WorkoutSession session = new WorkoutSession();
        session.setId(id);
        session.setWorkoutDate(date);
        session.setBodyPart(bodyPart);
        return session;
    }

    private User testUser() {
        User user = new User();
        user.setEmail("test@example.com");
        return user;
    }
}
