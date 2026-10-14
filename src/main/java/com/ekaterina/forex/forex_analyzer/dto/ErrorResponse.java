package com.ekaterina.forex.forex_analyzer.dto;

public class ErrorResponse {

    private String error;
    private Integer count;

    public ErrorResponse(String error) {
        this.error = error;
    }

    public ErrorResponse(String error, Integer count) {
        this.error = error;
        this.count = count;
    }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
}