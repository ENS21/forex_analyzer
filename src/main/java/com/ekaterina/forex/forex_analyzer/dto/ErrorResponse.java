package com.ekaterina.forex.forex_analyzer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
/**
 * Стандартный DTO для возврата информации об ошибке клиенту.
 * Используется, когда запрошенные данные не могут быть обработаны (например, не хватает свечей).
 */
@Getter
@Setter
@AllArgsConstructor
@Builder
@NoArgsConstructor
@Schema(description = "Ответ API в случае возникновения ошибки")

public class ErrorResponse {

    @Schema(description = "Текст ошибки", example = "Недостаточно свечей для расчёта RSI")
    private String error;

    @Schema(description = "Количество доступных данных (например, свечей", example = "10")
    private Integer count;
}