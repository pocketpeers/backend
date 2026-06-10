package com.pocketpeers.backend.operations.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Embeddable
public record OcrData(
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "json")
        Map<String,Object> dataFields
) {

}
