package com.example.example.controller;

import com.example.example.dto.MoveRequest;
import com.example.example.dto.ProduceStartRequest;
import com.example.example.dto.StorageDto;
import com.example.example.entity.UserSession;
import com.example.example.entity.board.Board;
import com.example.example.entity.board.PlacedObject;
import com.example.example.exception.CommonException;
import com.example.example.repository.UserSessionRepository;
import com.example.example.service.BoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;
    private final UserSessionRepository userSessionRepository;

    // ---------- чтение ----------

    @GetMapping("/by-session/{sessionCode}/user/{userId}")
    public ResponseEntity<Board> getMyBoard(@PathVariable String sessionCode,
                                            @PathVariable Long userId) {
        return ResponseEntity.ok(boardService.getBoardBySessionAndUser(sessionCode, userId));
    }

    @GetMapping("/{userSessionId}")
    public ResponseEntity<Board> getBoard(@PathVariable Long userSessionId) {
        return ResponseEntity.ok(boardService.getBoardByUserSession(userSessionId));
    }

    @GetMapping("/by-session/{sessionCode}/user/{userId}/storage")
    public ResponseEntity<StorageDto> getStorage(@PathVariable String sessionCode,
                                                 @PathVariable Long userId) {
        UserSession us = findUserSession(sessionCode, userId);
        return ResponseEntity.ok(StorageDto.from(boardService.getStorage(us)));
    }

    // ---------- действия по sessionCode + userId ----------

    @PostMapping("/by-session/{sessionCode}/user/{userId}/plant")
    public ResponseEntity<PlacedObject> plantByUser(@PathVariable String sessionCode,
                                                    @PathVariable Long userId,
                                                    @RequestBody PlantRequest req) {
        UserSession us = findUserSession(sessionCode, userId);
        return ResponseEntity.ok(boardService.plant(us, req.row(), req.col(),
                req.width(), req.height(), req.crop(), req.growSeconds()));
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/build")
    public ResponseEntity<PlacedObject> buildByUser(@PathVariable String sessionCode,
                                                    @PathVariable Long userId,
                                                    @RequestBody BuildRequest req) {
        UserSession us = findUserSession(sessionCode, userId);
        return ResponseEntity.ok(boardService.build(us, req.row(), req.col(),
                req.width(), req.height(), req.building(), req.level()));
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/harvest/{objectId}")
    public ResponseEntity<PlacedObject> harvestByUser(@PathVariable String sessionCode,
                                                      @PathVariable Long userId,
                                                      @PathVariable Long objectId) {
        UserSession us = findUserSession(sessionCode, userId);
        return ResponseEntity.ok(boardService.harvest(us, objectId));
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/produce/{objectId}/start")
    public ResponseEntity<PlacedObject> startProductionByUser(@PathVariable String sessionCode,
                                                              @PathVariable Long userId,
                                                              @PathVariable Long objectId,
                                                              @RequestBody(required = false) ProduceStartRequest req) {
        UserSession us = findUserSession(sessionCode, userId);
        String output = req != null ? req.outputCode() : null;
        return ResponseEntity.ok(boardService.startProduction(us, objectId, output));
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/produce/{objectId}/collect")
    public ResponseEntity<PlacedObject> collectProductionByUser(@PathVariable String sessionCode,
                                                                @PathVariable Long userId,
                                                                @PathVariable Long objectId,
                                                                @RequestParam(defaultValue = "-1") int index) {
        UserSession us = findUserSession(sessionCode, userId);
        return ResponseEntity.ok(boardService.collectSlot(us, objectId, index));
    }

    // ---------- старые эндпоинты по userSessionId ----------

    @PostMapping("/{userSessionId}/plant")
    public ResponseEntity<PlacedObject> plant(@PathVariable Long userSessionId,
                                              @RequestBody PlantRequest req) {
        UserSession us = findUserSession(userSessionId);
        return ResponseEntity.ok(boardService.plant(us, req.row(), req.col(),
                req.width(), req.height(), req.crop(), req.growSeconds()));
    }

    @PostMapping("/{userSessionId}/build")
    public ResponseEntity<PlacedObject> build(@PathVariable Long userSessionId,
                                              @RequestBody BuildRequest req) {
        UserSession us = findUserSession(userSessionId);
        return ResponseEntity.ok(boardService.build(us, req.row(), req.col(),
                req.width(), req.height(), req.building(), req.level()));
    }

    @PostMapping("/{userSessionId}/harvest/{objectId}")
    public ResponseEntity<PlacedObject> harvest(@PathVariable Long userSessionId,
                                                @PathVariable Long objectId) {
        UserSession us = findUserSession(userSessionId);
        return ResponseEntity.ok(boardService.harvest(us, objectId));
    }

    @PostMapping("/{userSessionId}/produce/{objectId}/start")
    public ResponseEntity<PlacedObject> startProduction(@PathVariable Long userSessionId,
                                                        @PathVariable Long objectId,
                                                        @RequestBody(required = false) ProduceStartRequest req) {
        UserSession us = findUserSession(userSessionId);
        String output = req != null ? req.outputCode() : null;
        return ResponseEntity.ok(boardService.startProduction(us, objectId, output));
    }

    @PostMapping("/{userSessionId}/produce/{objectId}/collect")
    public ResponseEntity<PlacedObject> collectProduction(@PathVariable Long userSessionId,
                                                          @PathVariable Long objectId) {
        UserSession us = findUserSession(userSessionId);
        return ResponseEntity.ok(boardService.collectProduction(us, objectId));
    }

    @GetMapping("/{userSessionId}/storage")
    public ResponseEntity<StorageDto> getStorageById(@PathVariable Long userSessionId) {
        UserSession us = findUserSession(userSessionId);
        return ResponseEntity.ok(StorageDto.from(boardService.getStorage(us)));
    }

    @PostMapping("/by-session/{sessionCode}/user/{userId}/move/{objectId}")
    public ResponseEntity<PlacedObject> moveByUser(@PathVariable String sessionCode,
                                                   @PathVariable Long userId,
                                                   @PathVariable Long objectId,
                                                   @RequestBody MoveRequest req) {
        UserSession us = findUserSession(sessionCode, userId);
        return ResponseEntity.ok(boardService.move(us, objectId, req.row(), req.col()));
    }


    @PostMapping("/by-session/{sessionCode}/user/{userId}/remove/{objectId}")
    public ResponseEntity<PlacedObject> removeByUser(@PathVariable String sessionCode,
                                                     @PathVariable Long userId,
                                                     @PathVariable Long objectId) {
        UserSession us = findUserSession(sessionCode, userId);
        return ResponseEntity.ok(boardService.remove(us, objectId));
    }

    /** Убрать продукт из очереди производства (ингредиенты вернутся на склад). */
    @PostMapping("/by-session/{sessionCode}/user/{userId}/produce/{objectId}/queue/{index}/cancel")
    public ResponseEntity<PlacedObject> cancelQueuedByUser(@PathVariable String sessionCode,
                                                           @PathVariable Long userId,
                                                           @PathVariable Long objectId,
                                                           @PathVariable int index) {
        UserSession us = findUserSession(sessionCode, userId);
        return ResponseEntity.ok(boardService.cancelQueued(us, objectId, index));
    }

    // ---------- helpers ----------

    private UserSession findUserSession(Long userSessionId) {
        return userSessionRepository.findById(userSessionId)
                .orElseThrow(() -> new CommonException("UserSession not found"));
    }

    private UserSession findUserSession(String sessionCode, Long userId) {
        return userSessionRepository
                .findBySessionCodeAndUserId(sessionCode.toUpperCase(), userId)
                .orElseThrow(() -> new CommonException("UserSession not found"));
    }

    public record PlantRequest(int row, int col, int width, int height,
                               String crop, int growSeconds) {}
    public record BuildRequest(int row, int col, int width, int height,
                               String building, int level) {}
}