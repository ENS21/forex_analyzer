package com.ekaterina.forex.forex_analyzer.dto;
import lombok.*;
/**
 * DTO для передачи информации об одном уровне поддержки или сопротивления.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LevelDto {
    private  String type;      // "SUPPORT" или "RESISTANCE"
    private double price;
    private  String time;      // дата свечи, где найден уровень
}