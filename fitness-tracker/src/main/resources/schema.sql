-- Spring Security 的 PersistentTokenBasedRememberMeServices（見 SecurityConfig.persistentTokenRepository）
-- 需要的表，欄位/型別是 JdbcTokenRepositoryImpl 固定要求的格式，不能改名字。
-- IF NOT EXISTS：每次啟動都會跑這支 script，避免第二次啟動時因為表已存在而報錯。
CREATE TABLE IF NOT EXISTS persistent_logins (
    username VARCHAR(64) NOT NULL,
    series VARCHAR(64) PRIMARY KEY,
    token VARCHAR(64) NOT NULL,
    last_used TIMESTAMP NOT NULL
);
