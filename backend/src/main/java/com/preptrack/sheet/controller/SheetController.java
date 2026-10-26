package com.preptrack.sheet.controller;

import com.preptrack.auth.security.UserDetailsImpl;
import com.preptrack.sheet.dto.AvailableSheetResponse;
import com.preptrack.sheet.dto.SheetProblemResponse;
import com.preptrack.sheet.dto.UserSheetResponse;
import com.preptrack.sheet.service.SheetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SheetController {

    private final SheetService sheetService;

    @GetMapping("/api/sheets/available")
    public ResponseEntity<List<AvailableSheetResponse>> getAvailableSheets() {
        return ResponseEntity.ok(sheetService.getAvailableSheets());
    }

    @GetMapping("/api/user/sheets")
    public ResponseEntity<List<UserSheetResponse>> getUserSheets(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails instanceof UserDetailsImpl impl) {
            return ResponseEntity.ok(sheetService.getUserSheets(impl.getId()));
        }
        return ResponseEntity.ok(sheetService.getUserSheets(userDetails.getUsername()));
    }

    @GetMapping("/api/user/sheets/{sheetId}/problems")
    public ResponseEntity<List<SheetProblemResponse>> getSheetProblems(
            @PathVariable Long sheetId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails instanceof UserDetailsImpl impl) {
            return ResponseEntity.ok(sheetService.getSheetProblems(sheetId, impl.getId()));
        }
        return ResponseEntity.ok(sheetService.getSheetProblems(sheetId, userDetails != null ? userDetails.getUsername() : null));
    }
}
