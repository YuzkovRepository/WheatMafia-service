package com.example.example.entity.board;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Setter;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Cell {

    public enum Type {
        EMPTY,       // свободна
        OCCUPIED,    // занята объектом (грядка/здание)
        LOCKED,      // заблокирована (открывается за деньги)
        BLOCKED      // временно занята (стройка, событие)
    }

    private int index;           // 0..99
    private Type type = Type.EMPTY;
    private Long objectId;       // ссылка на PlacedObject, если OCCUPIED

    public Cell() {}

    public Cell(int index) {
        this.index = index;
    }

    // getters/setters
    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public Long getObjectId() { return objectId; }
    public void setObjectId(Long objectId) { this.objectId = objectId; }
}