package com.example.example.service;

import com.example.example.entity.UserSession;
import com.example.example.entity.board.Board;
import com.example.example.entity.board.PlacedObject;
import com.example.example.entity.board.Storage;

public interface BoardService {

    Board getBoardBySessionAndUser(String sessionCode, Long userId);
    Board getBoardByUserSession(Long userSessionId);

    PlacedObject plant(UserSession userSession, int row, int col,
                       int width, int height, String crop, int growSeconds);
    PlacedObject build(UserSession userSession, int row, int col,
                       int width, int height, String building, int level);
    PlacedObject harvest(UserSession userSession, long objectId);

    // --- производство ---
    PlacedObject startProduction(UserSession userSession, long objectId, String outputCode);
    PlacedObject collectProduction(UserSession userSession, long objectId);

    // --- склад ---
    Storage getStorage(UserSession userSession);

    PlacedObject move(UserSession us, long objectId, int row, int col);

    PlacedObject remove(UserSession us, long objectId);
    PlacedObject cancelQueued(UserSession us, long objectId, int index);
    PlacedObject collectSlot(UserSession us, long objectId, int index);
}