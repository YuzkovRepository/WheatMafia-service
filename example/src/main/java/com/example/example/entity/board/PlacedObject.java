package com.example.example.entity.board;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlacedObject {

    public enum Kind { CROP, BUILDING }

    private Long id;               // уникальный в рамках Board
    private Kind kind;

    // координаты и размер (левый верхний угол + размер)
    private int row;               // 0..9
    private int col;               // 0..9
    private int width;             // 1..10
    private int height;            // 1..10

    // для CROP
    private String crop;           // "wheat", "grape", "carrot"...
    private Instant plantedAt;
    private Instant readyAt;
    private Integer growSeconds;

    // для BUILDING
    private String building;       // "MILL", "WINERY", "FACTORY"...
    private Integer level;
    private Instant builtAt;

    // производство (для BUILDING)
    private Instant productionStartedAt;
    private Instant productionReadyAt;
    private String productionInput;
    private Integer productionInputQty;
    private String productionOutput;
    private Integer productionOutputQty;

    private Integer productionTime;
    private String recipeCode;

    /** Забастовка: до этого момента здание не принимает новое производство. */
    private Instant blockedUntil;
    public Instant getBlockedUntil() { return blockedUntil; }
    public void setBlockedUntil(Instant blockedUntil) { this.blockedUntil = blockedUntil; }

    /**
     * Ячейки производства (для BUILDING), максимум 3. Продукты делаются по очереди сами,
     * готовый можно забрать независимо от остальных. Ингредиенты списаны при постановке.
     */
    private List<QueuedJob> queue = new ArrayList<>();

    public static class QueuedJob {
        private String recipeCode;               // «factory:output»
        private String output;
        private Integer outputQty;
        private Integer time;                    // секунд на производство
        private Map<String, Integer> inputs = new LinkedHashMap<>(); // что списано (вернётся при отмене)
        private Instant startedAt;
        private Instant readyAt;

        public Instant getStartedAt() { return startedAt; }
        public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
        public Instant getReadyAt() { return readyAt; }
        public void setReadyAt(Instant readyAt) { this.readyAt = readyAt; }

        public String getRecipeCode() { return recipeCode; }
        public void setRecipeCode(String recipeCode) { this.recipeCode = recipeCode; }
        public String getOutput() { return output; }
        public void setOutput(String output) { this.output = output; }
        public Integer getOutputQty() { return outputQty; }
        public void setOutputQty(Integer outputQty) { this.outputQty = outputQty; }
        public Integer getTime() { return time; }
        public void setTime(Integer time) { this.time = time; }
        public Map<String, Integer> getInputs() { return inputs; }
        public void setInputs(Map<String, Integer> inputs) { this.inputs = inputs; }
    }

    public List<QueuedJob> getQueue() {
        if (queue == null) queue = new ArrayList<>(); // старые доски без очереди
        return queue;
    }
    public void setQueue(List<QueuedJob> queue) { this.queue = queue; }

    public Integer getProductionTime() { return productionTime; }
    public void setProductionTime(Integer productionTime) { this.productionTime = productionTime; }
    public String getRecipeCode() { return recipeCode; }
    public void setRecipeCode(String recipeCode) { this.recipeCode = recipeCode; }

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public int getRow() { return row; }
    public void setRow(int row) { this.row = row; }
    public int getCol() { return col; }
    public void setCol(int col) { this.col = col; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public String getCrop() { return crop; }
    public void setCrop(String crop) { this.crop = crop; }
    public Instant getPlantedAt() { return plantedAt; }
    public void setPlantedAt(Instant plantedAt) { this.plantedAt = plantedAt; }
    public Instant getReadyAt() { return readyAt; }
    public void setReadyAt(Instant readyAt) { this.readyAt = readyAt; }
    public Integer getGrowSeconds() { return growSeconds; }
    public void setGrowSeconds(Integer growSeconds) { this.growSeconds = growSeconds; }
    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public Instant getBuiltAt() { return builtAt; }
    public void setBuiltAt(Instant builtAt) { this.builtAt = builtAt; }
    public Instant getProductionStartedAt() { return productionStartedAt; }
    public void setProductionStartedAt(Instant productionStartedAt) { this.productionStartedAt = productionStartedAt; }
    public Instant getProductionReadyAt() { return productionReadyAt; }
    public void setProductionReadyAt(Instant productionReadyAt) { this.productionReadyAt = productionReadyAt; }
    public String getProductionInput() { return productionInput; }
    public void setProductionInput(String productionInput) { this.productionInput = productionInput; }
    public Integer getProductionInputQty() { return productionInputQty; }
    public void setProductionInputQty(Integer productionInputQty) { this.productionInputQty = productionInputQty; }
    public String getProductionOutput() { return productionOutput; }
    public void setProductionOutput(String productionOutput) { this.productionOutput = productionOutput; }
    public Integer getProductionOutputQty() { return productionOutputQty; }
    public void setProductionOutputQty(Integer productionOutputQty) { this.productionOutputQty = productionOutputQty; }
}