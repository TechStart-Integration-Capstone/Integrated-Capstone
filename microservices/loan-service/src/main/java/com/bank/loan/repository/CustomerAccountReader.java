package com.bank.loan.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Read-only access to CUSTOMER and ACCOUNT (same Azure SQL database).
 * loan-service never writes ACCOUNT — balances only move through transaction-service.
 */
@Repository
public class CustomerAccountReader {

    private final JdbcTemplate jdbc;

    public CustomerAccountReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record AccountRow(Long accountId, Long customerId, String accountNumber, String status, String currency) {}

    public record CustomerRow(Long customerId, int creditScore, BigDecimal monthlyIncome) {}

    public Optional<AccountRow> findAccountByNumber(String accountNumber) {
        return jdbc.query("SELECT account_id, customer_id, account_number, status, currency FROM dbo.ACCOUNT WHERE account_number = ?",
                (rs, i) -> new AccountRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5)),
                accountNumber).stream().findFirst();
    }

    public Optional<AccountRow> findAccountById(Long accountId) {
        return jdbc.query("SELECT account_id, customer_id, account_number, status, currency FROM dbo.ACCOUNT WHERE account_id = ?",
                (rs, i) -> new AccountRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5)),
                accountId).stream().findFirst();
    }

    public Optional<CustomerRow> findCustomer(Long customerId) {
        return jdbc.query("SELECT customer_id, credit_score, monthly_income FROM dbo.CUSTOMER WHERE customer_id = ?",
                (rs, i) -> new CustomerRow(rs.getLong(1), rs.getInt(2), rs.getBigDecimal(3)),
                customerId).stream().findFirst();
    }
}
