package fitness_tracker.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import fitness_tracker.entity.User;
import fitness_tracker.service.CurrentUserService;
import fitness_tracker.service.WorkoutPlanService;
import fitness_tracker.service.WorkoutPlanService.DayComposition;

@WebMvcTest(PlanApiController.class)
@WithMockUser(username = "test@example.com")
class PlanApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkoutPlanService workoutPlanService;

    @MockBean
    private CurrentUserService currentUserService;

    @Test
    void queueReturnsDayCompositionsForGivenDays() throws Exception {
        User user = new User();
        user.setEmail("test@example.com");
        given(currentUserService.getCurrentUser()).willReturn(user);
        given(workoutPlanService.currentQueueComposition(user, 3)).willReturn(List.of(
                new DayComposition("推日", List.of("臥推", "肩推"), List.of("側平舉", "三頭下壓"))
        ));

        mockMvc.perform(get("/plan/api/queue").param("days", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dayName").value("推日"))
                .andExpect(jsonPath("$[0].mainNames[0]").value("臥推"))
                .andExpect(jsonPath("$[0].accessoryPool[0]").value("側平舉"));
    }

    @Test
    void nextReturnsSingleDayComposition() throws Exception {
        User user = new User();
        user.setEmail("test@example.com");
        given(currentUserService.getCurrentUser()).willReturn(user);
        given(workoutPlanService.nextCompositionInCycle(user, "腿日", 3)).willReturn(
                new DayComposition("推日", List.of("臥推", "肩推"), List.of("側平舉"))
        );

        mockMvc.perform(get("/plan/api/next").param("lastDay", "腿日").param("days", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayName").value("推日"));
    }
}
