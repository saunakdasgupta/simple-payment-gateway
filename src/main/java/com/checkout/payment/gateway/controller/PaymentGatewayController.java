package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.ErrorResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payments", description = "Process card payments and retrieve payment details")
@RestController("api")
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  @Operation(summary = "Retrieve a payment by ID")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Payment found"),
      @ApiResponse(responseCode = "404", description = "Payment not found",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping("/payment/{id}")
  public ResponseEntity<PostPaymentResponse> getPostPaymentEventById(@PathVariable UUID id) {
    return new ResponseEntity<>(paymentGatewayService.getPaymentById(id), HttpStatus.OK);
  }

  @Operation(summary = "Process a new card payment",
      description = "Validates the request, calls the acquiring bank, and returns the payment outcome. "
          + "Returns 400 if the request fails validation (status will be Rejected). "
          + "Returns 502 if the bank is temporarily unavailable.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Payment authorized or declined by the bank"),
      @ApiResponse(responseCode = "400", description = "Payment rejected due to validation failure"),
      @ApiResponse(responseCode = "502", description = "Bank unavailable",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/payment")
  public ResponseEntity<PostPaymentResponse> processPayment(@RequestBody PostPaymentRequest request) {
    PostPaymentResponse response = paymentGatewayService.processPayment(request);
    HttpStatus status = response.getStatus() == PaymentStatus.REJECTED
        ? HttpStatus.BAD_REQUEST
        : HttpStatus.OK;
    return new ResponseEntity<>(response, status);
  }
}
