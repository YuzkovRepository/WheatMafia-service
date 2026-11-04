package com.example.example.entity.board;

import java.util.HashMap;
import java.util.Map;

public class Storage {

    private int capacity = 50;
    private Map<String, Integer> items = new HashMap<>();

    public int used() {
        return items.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean canAdd(String item, int qty) {
        return used() + qty <= capacity;
    }

    public boolean has(String item, int qty) {
        return items.getOrDefault(item, 0) >= qty;
    }

    public boolean remove(String item, int qty) {
        int have = items.getOrDefault(item, 0);
        if (have < qty) return false;
        if (have == qty) items.remove(item);
        else items.put(item, have - qty);
        return true;
    }

    public void add(String item, int qty) {
        items.merge(item, qty, Integer::sum);
    }

    public Map<String, Integer> snapshot() {
        return Map.copyOf(items);
    }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public Map<String, Integer> getItems() { return items; }
    public void setItems(Map<String, Integer> items) { this.items = items; }
}