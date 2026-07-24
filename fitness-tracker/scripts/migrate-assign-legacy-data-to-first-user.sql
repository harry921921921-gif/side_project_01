-- 一次性資料遷移：把 Stage 2 上線前留下的 user_id = NULL 舊資料，
-- 指派給第一位註冊的使用者（users 表中 id 最小的那筆）。
-- 用法：確認 users 表至少有一筆帳號後，直接對 fitness_tracker 資料庫執行一次即可。
-- 這段是「冪等」的（重複執行沒有副作用）：已經有 user_id 的紀錄不會被覆蓋，
-- 執行完後 WHERE user_id IS NULL 就撈不到東西了。

UPDATE body_weight
SET user_id = (SELECT id FROM users ORDER BY id ASC LIMIT 1)
WHERE user_id IS NULL;

UPDATE workout_session
SET user_id = (SELECT id FROM users ORDER BY id ASC LIMIT 1)
WHERE user_id IS NULL;

-- 驗證：兩段都應該回傳 0
-- SELECT COUNT(*) FROM body_weight WHERE user_id IS NULL;
-- SELECT COUNT(*) FROM workout_session WHERE user_id IS NULL;
