package com.bankingsim.controller;

import com.bankingsim.dao.UserDao;
import com.bankingsim.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api")
public class AdminUserController {

    @Autowired
    private UserDao userDao;

    // --------------------------------------------------------
    // GET ALL ADMIN USERS
    // --------------------------------------------------------
    @GetMapping("/admin-users")
    public List<User> getAllAdmins() {
        return userDao.getAllAdmins();
    }

    // --------------------------------------------------------
    // DELETE ADMIN USER
    // --------------------------------------------------------
    @DeleteMapping("/admin-users/{username}")
    public Map<String, Object> deleteAdmin(
            @PathVariable String username,
            @RequestHeader("X-Admin-Username") String loggedInAdmin
    ) {

        // ❌ Prevent deleting default admin
        if ("admin".equalsIgnoreCase(username)) {
            return Map.of(
                    "success", false,
                    "message", "The default admin account cannot be deleted."
            );
        }

        // ❌ Prevent deleting yourself
        if (username.equalsIgnoreCase(loggedInAdmin)) {
            return Map.of(
                    "success", false,
                    "message", "You cannot delete your own admin account."
            );
        }

        boolean exists = userDao.usernameExists(username);
        if (!exists) {
            return Map.of(
                    "success", false,
                    "message", "Admin user not found."
            );
        }

        boolean deleted = userDao.deleteAdmin(username);

        return Map.of(
                "success", deleted,
                "message", deleted ? "Admin deleted successfully." : "Failed to delete admin."
        );
    }

    // --------------------------------------------------------
    // UPDATE ADMIN EMAIL / PHONE (only allowed if the caller is the default 'admin')
    // --------------------------------------------------------
    @PutMapping("/admin-users/{username}")
    public Map<String, Object> updateAdminContact(
            @PathVariable String username,
            @RequestHeader("X-Admin-Username") String loggedInAdmin,
            @RequestBody Map<String, Object> body
    ) {
        // Only allow the special 'admin' user to perform contact updates
        if (loggedInAdmin == null || ! "admin".equalsIgnoreCase(loggedInAdmin.trim())) {
            return Map.of(
                    "success", false,
                    "message", "Only the default 'admin' user may update admin contact details."
            );
        }

        boolean exists = userDao.usernameExists(username);
        if (!exists) {
            return Map.of(
                    "success", false,
                    "message", "Admin user not found."
            );
        }

        // Accept email and/or phone from request body (both optional)
        String email = null;
        String phone = null;
        try {
            if (body.containsKey("email") && body.get("email") != null) {
                email = body.get("email").toString().trim();
            }
            if (body.containsKey("phone") && body.get("phone") != null) {
                phone = body.get("phone").toString().trim();
            }
        } catch (Exception e) {
            // ignore parsing errors
        }

        if ((email == null || email.isEmpty()) && (phone == null || phone.isEmpty())) {
            return Map.of(
                    "success", false,
                    "message", "No email or phone provided to update."
            );
        }

        User u = new User();
        u.setEmail(email);
        u.setPhone(phone);

        boolean updated = userDao.updateAdmin(username, u);

        return Map.of(
                "success", updated,
                "message", updated ? "Admin contact updated successfully." : "Failed to update admin contact."
        );
    }
}
