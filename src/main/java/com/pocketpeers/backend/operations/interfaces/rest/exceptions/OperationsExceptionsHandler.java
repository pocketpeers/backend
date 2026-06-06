package com.pocketpeers.backend.operations.interfaces.rest.exceptions;

import com.pocketpeers.backend.groups.domain.exceptions.PaymentNotFoundException;
import com.pocketpeers.backend.operations.domain.exceptions.ExpenseNotFoundException;
import com.pocketpeers.backend.operations.domain.exceptions.ReceiptImageProcessingException;
import com.pocketpeers.backend.operations.domain.exceptions.ReceiptNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class OperationsExceptionsHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Map<String,String>> handle(PaymentNotFoundException ex){
        Map<String, String> error = new HashMap<>();
        error.put("error", "Not Found");
        error.put("message", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ReceiptNotFoundException.class)
    public ResponseEntity<Map<String,String>> handle(ReceiptNotFoundException ex){
        Map<String, String> error = new HashMap<>();
        error.put("error", "Not Found");
        error.put("message", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ExpenseNotFoundException.class)
    public ResponseEntity<Map<String,String>> handle(ExpenseNotFoundException ex){
        Map<String, String> error = new HashMap<>();
        error.put("error", "Not Found");
        error.put("message", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ReceiptImageProcessingException.class)
    public ResponseEntity<Map<String,String>> handle(ReceiptImageProcessingException ex){
        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal Server Error");
        error.put("message", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
