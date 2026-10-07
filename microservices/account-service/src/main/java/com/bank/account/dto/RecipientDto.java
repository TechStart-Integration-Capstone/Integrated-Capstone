package com.bank.account.dto;

import java.util.List;

public record RecipientDto(String accountNumber, String fullName, boolean favorite) {
    public record DirectoryDto(List<RecipientDto> favorites, List<RecipientDto> recent) {}
}
