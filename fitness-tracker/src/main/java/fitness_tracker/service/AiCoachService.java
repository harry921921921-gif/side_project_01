package fitness_tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fitness_tracker.entity.CoachAdvice;
import fitness_tracker.entity.User;
import fitness_tracker.repository.CoachAdviceRepository;
import fitness_tracker.service.SnapshotService.CoachSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

// AI 教練：讀快照 -> Spring AI(Anthropic) 生成教練口吻建議；快取一天一次；
// LLM 失敗或沒金鑰就退回「用真實數據的規則版」，永不空白。
@Service
public class AiCoachService {

    private static final Logger log = LoggerFactory.getLogger(AiCoachService.class);

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final SnapshotService snapshotService;
    private final CoachAdviceRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public AiCoachService(ObjectProvider<ChatModel> chatModelProvider, SnapshotService snapshotService, CoachAdviceRepository repo) {
        this.chatModelProvider = chatModelProvider;
        this.snapshotService = snapshotService;
        this.repo = repo;
    }

    private record Advice(String status, String exec, String today, String tomorrow) {}

    private static final String SYSTEM = """
            你是使用者的私人重訓教練，語氣溫暖、鼓勵、但務實，你會關注他的訓練節奏。
            規則：
            - 只能引用下方提供的數據，嚴禁虛構任何重量、次數、日期。
            - 輸出繁體中文，像教練在對他講話，不要條列、不要emoji。
            - 只回傳 JSON，格式：{"status":"ON_TRACK|SLIPPING|CRUSHING|CAUTION","executionLine":"","todayLine":"","tomorrowLine":""}
            - executionLine 講本週執行力；todayLine 講今天該做什麼；tomorrowLine 幫他預熱明天。每句 40 字內。
            """;

    @Transactional
    public CoachAdvice getTodayAdvice(User user) {
        CoachSnapshot snap = snapshotService.build(user);
        String hash = snap.hash();
        LocalDate today = LocalDate.now();

        CoachAdvice existing = repo.findByUserAndGeneratedDate(user, today).orElse(null);
        if (existing != null && hash.equals(existing.getSnapshotHash())) {
            return existing;   // 今天已生成且資料沒變 -> 直接用快取，不重打 LLM
        }

        Advice a = generate(snap);

        // 健康守門：有警訊一律 CAUTION，並蓋掉今天的建議為恢復導向
        if (snap.caution()) {
            a = new Advice("CAUTION", a.exec(), "偵測到疼痛或連續失敗，今天建議休息或降強度，別硬撐。", a.tomorrow());
        }

        CoachAdvice entity = (existing != null) ? existing : new CoachAdvice();
        entity.setUser(user);
        entity.setGeneratedDate(today);
        entity.setSnapshotHash(hash);
        entity.setStatus(a.status());
        entity.setExecutionLine(a.exec());
        entity.setTodayLine(a.today());
        entity.setTomorrowLine(a.tomorrow());
        return repo.save(entity);
    }

    private Advice generate(CoachSnapshot snap) {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model != null) {
            try {
                String content = ChatClient.create(model)
                        .prompt()
                        .system(SYSTEM)
                        .user(snap.toPrompt())
                        .call()
                        .content();
                Advice a = parse(content);
                if (a != null) return a;
            } catch (Exception e) {
                log.warn("AI 教練 LLM 呼叫失敗，改用規則版：{}", e.getMessage());
            }
        }
        return fallback(snap);
    }

    private Advice parse(String content) {
        try {
            int i = content.indexOf('{'), j = content.lastIndexOf('}');
            if (i < 0 || j < i) return null;
            JsonNode n = mapper.readTree(content.substring(i, j + 1));
            return new Advice(text(n, "status", "ON_TRACK"), text(n, "executionLine", ""),
                    text(n, "todayLine", ""), text(n, "tomorrowLine", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private String text(JsonNode n, String k, String d) {
        return (n.has(k) && !n.get(k).isNull()) ? n.get(k).asText() : d;
    }

    // 規則版（用真實快照資料，比原本「保持節奏」聰明很多）
    private Advice fallback(CoachSnapshot s) {
        String exec;
        if (s.planned() > 0 && s.completed() >= s.planned()) exec = "本週 " + s.planned() + " 練你全部完成，執行力很棒！";
        else if (s.completed() == 0) exec = "這週還沒開始練，找個時間動起來吧。";
        else exec = "本週計畫 " + s.planned() + " 練，已完成 " + s.completed() + " 次，繼續保持。";

        var t = s.today();
        var tm = s.tomorrow();
        String today = t.training()
                ? "今天練" + t.dayName() + (t.mainLifts().isEmpty() ? "" : "，主項" + String.join("、", t.mainLifts())) + "，好好專注每一組。"
                : "今天是休息日，睡飽、補足蛋白質，讓身體回血。";
        String tomorrow = tm.training()
                ? "明天練" + tm.dayName() + "，今晚早點睡，養足精神。"
                : "明天休息，好好放鬆。";

        String status = s.caution() ? "CAUTION"
                : (s.planned() > 0 && s.completed() >= s.planned()) ? "CRUSHING"
                : (s.completed() == 0) ? "SLIPPING" : "ON_TRACK";
        return new Advice(status, exec, today, tomorrow);
    }
}
