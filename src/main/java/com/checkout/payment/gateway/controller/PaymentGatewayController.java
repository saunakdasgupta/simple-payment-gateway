package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.ErrorResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payments", description = "Process card payments and retrieve payment details")
@RestController
public class PaymentGatewayController {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayController.class);

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  @Operation(summary = "Retrieve a payment by ID")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Payment found"),
      @ApiResponse(responseCode = "400", description = "Invalid payment ID format (not a UUID)",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(responseCode = "404", description = "Payment not found",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping("/payments/{id}")
  public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable UUID id) {
    LOG.info("GET /payments/{}", id);
    return ResponseEntity.ok(paymentGatewayService.getPaymentById(id));
  }

  @Operation(summary = "Process a new card payment",
      description = "Validates the request, calls the acquiring bank, and returns the payment outcome. "
          + "Returns 400 if the request fails validation (status will be Rejected). "
          + "Returns 502 if the bank is temporarily unavailable.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Payment authorized or declined by the bank"),
      @ApiResponse(responseCode = "400", description = "Payment rejected due to validation failure",
          content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
      @ApiResponse(responseCode = "502", description = "Bank unavailable",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/payments")
  public ResponseEntity<PaymentResponse> processPayment(@RequestBody PostPaymentRequest request) {
    LOG.info("POST /payments {}", request);
    PaymentResponse response = paymentGatewayService.processPayment(request);
    HttpStatus status = response.getStatus() == PaymentStatus.REJECTED
        ? HttpStatus.BAD_REQUEST
        : HttpStatus.OK;
    return new ResponseEntity<>(response, status);
  }
}
