package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class CommonExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(PaymentNotFoundException.class)
  public ResponseEntity<ErrorResponse> handlePaymentNotFoundException(PaymentNotFoundException ex) {
    // Service already logged with full context (payment ID); no additional logging needed here
    return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(BankUnavailableException.class)
  public ResponseEntity<ErrorResponse> handleBankUnavailableException(BankUnavailableException ex) {
    LOG.warn("Bank unavailable: {}", ex.getMessage());
    return new ResponseEntity<>(new ErrorResponse("Payment processing temporarily unavailable"),
        HttpStatus.BAD_GATEWAY);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    return new ResponseEntity<>(
        new ErrorResponse("Invalid ID format: expected a UUID"),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
    // Catches anything not handled above — e.g. a RuntimeException thrown when the bank
    // rejects the gateway's own request with a 4xx. requestId is still in MDC here
    // (the RequestCorrelationFilter finally block has not run yet), so this log line
    // is correlated to the originating request.
    LOG.error("Unexpected error processing request", ex);
    return new ResponseEntity<>(new ErrorResponse("An unexpected error occurred"),
        HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
