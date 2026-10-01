-- ============================================================================
-- Script: seed_accounts.sql
-- Generates 1,000 customer accounts in Oracle XE / H2 for 800 TPS stress test
-- ============================================================================

DECLARE
    v_cust_id NUMBER;
    v_acc_num VARCHAR2(30);
BEGIN
    FOR i IN 1..1000 LOOP
        INSERT INTO CUSTOMER (username, password_hash, first_name, last_name, email, contact_no, status)
        VALUES ('perf_user_' || i, '$2a$10$wN3WpZgJ4g7N8dC5lRzPfeYk4GqU1xL8e9m3K7b0yU6r5T1w9P8a2', 
                'PerfFirstName' || i, 'PerfLastName' || i, 'perf' || i || '@paypink.ph', '+63 917 ' || LPAD(i, 7, '0'), 'ACTIVE')
        RETURNING customer_id INTO v_cust_id;

        v_acc_num := 'ACC-PH-LOAD-' || LPAD(i, 6, '0');

        INSERT INTO ACCOUNT (customer_id, account_number, account_type, currency, current_balance, status)
        VALUES (v_cust_id, v_acc_num, 'SAVINGS_ACCOUNT', 'PHP', 500000.0000, 'ACTIVE');
    END LOOP;
    COMMIT;
END;
/
