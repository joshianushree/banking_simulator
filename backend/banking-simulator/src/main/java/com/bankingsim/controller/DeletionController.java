package com.bankingsim.controller;

import com.bankingsim.model.DeletionRequest;
import com.bankingsim.service.DeletionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deletion")
@CrossOrigin( origins = "http://localhost:3000",
        allowCredentials = "true")
public class DeletionController {

    private final DeletionService deletionService;

    public DeletionController(DeletionService deletionService) {
        this.deletionService = deletionService;
    }

    // ============================================================
    // CUSTOMER — SUBMIT REQUEST
    // ============================================================
    @PostMapping("/request")
    public Response submitDeletionRequest(@RequestBody DeletionRequest req) {

        boolean success = deletionService.submitDeletionRequest(req);

        if (!success) {
            return new Response(false, "Failed to submit account deletion request.");
        }

        return new Response(true, "Your account deletion request has been submitted. Please check after 48 hours.");
    }

    // ============================================================
    // ADMIN — LIST PENDING REQUESTS
    // ============================================================
    @GetMapping("/pending")
    public List<DeletionRequest> getPendingRequests() {
        return deletionService.getPendingRequests();
    }

    // ============================================================
    // ADMIN — GET REQUEST DETAILS
    // ============================================================
    @GetMapping("/{id}")
    public DeletionRequest getRequestById(@PathVariable long id) {
        return deletionService.getRequestById(id);
    }

    // ============================================================
    // ADMIN — APPROVE REQUEST
    // ============================================================
    @PostMapping("/{id}/approve")
    public Response approveRequest(@PathVariable long id,
                                   @RequestParam String admin,
                                   @RequestParam(required = false) String comment) {

        boolean ok = deletionService.approveRequest(id, admin, comment);

        if (!ok) {
            return new Response(false, "Cannot approve request. (Possibly due to outstanding loan)");
        }

        return new Response(true, "Account deletion approved successfully.");
    }

    // ============================================================
    // ADMIN — REJECT REQUEST
    // ============================================================
    @PostMapping("/{id}/reject")
    public Response rejectRequest(@PathVariable long id,
                                  @RequestParam String admin,
                                  @RequestParam(required = false) String comment) {

        boolean ok = deletionService.rejectRequest(id, admin, comment);

        if (!ok) {
            return new Response(false, "Failed to reject deletion request.");
        }

        return new Response(true, "Account deletion request rejected.");
    }

    // ============================================================
    // SIMPLE RESPONSE WRAPPER
    // ============================================================
    public record Response(boolean success, String message) {}
}
