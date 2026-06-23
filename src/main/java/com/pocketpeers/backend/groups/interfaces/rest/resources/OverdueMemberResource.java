package com.pocketpeers.backend.groups.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OverdueMemberResource(
        Long userId,
        String fullName,
        String photo,
        BigDecimal overdueAmount,
        LocalDate oldestDueDate,
        long maxDaysOverdue,
        int overduePaymentsCount
) {
}
