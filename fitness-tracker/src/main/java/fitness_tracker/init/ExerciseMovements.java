package fitness_tracker.init;

import java.util.HashMap;
import java.util.Map;

// 動作 → 動作型態(PUSH/PULL/LEGS/CORE)，決定 PPL 分割排哪一天。種子與回填 runner 共用。
public final class ExerciseMovements {

    private static final Map<String, String> MAP = new HashMap<>();

    private static void put(String movement, String... names) {
        for (String n : names) MAP.put(n, movement);
    }

    static {
        put("PUSH", "臥推", "上斜臥推", "下斜臥推", "啞鈴臥推", "上斜啞鈴推", "胸推機", "伏地挺身",
                "飛鳥", "纜繩夾胸", "蝴蝶機夾胸",
                "肩推", "啞鈴肩推", "阿諾德推舉", "側平舉", "繩索側舉", "前平舉", "聳肩",
                "窄握推", "三頭下壓", "繩索三頭下壓", "三頭伸展", "雙槓臂屈伸");
        put("PULL", "硬舉", "引體向上", "滑輪下拉", "正手滑輪下拉", "槓鈴划船", "坐姿划船",
                "啞鈴單臂划船", "T槓划船", "直臂下壓",
                "反向飛鳥", "面拉",
                "二頭彎舉", "啞鈴彎舉", "錘式彎舉", "斜托彎舉", "繩索彎舉", "集中彎舉");
        put("LEGS", "深蹲", "前蹲", "羅馬尼亞硬舉", "腿推", "保加利亞分腿蹲", "弓步蹲",
                "腿彎舉", "腿伸展", "小腿舉", "坐姿小腿提升");
        put("CORE", "棒式", "捲腹", "仰臥起坐", "懸垂舉腿", "俄羅斯轉體", "滾輪", "側棒式");
    }

    private ExerciseMovements() {}

    // 找不到就回 null（使用者自訂動作可日後再補）
    public static String movementFor(String name) {
        return name == null ? null : MAP.get(name);
    }
}
