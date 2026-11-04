package com.example.example.entity.board;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Board {

    public static final int DEFAULT_SIZE = 24;
    public static final String WAREHOUSE = "warehouse";

    /** Размер склада (ширина × высота в клетках). Должен совпадать с каталогом (SeedData). */
    public static final int WAREHOUSE_WIDTH = 3;
    public static final int WAREHOUSE_HEIGHT = 2;

    /**
     * Где появляется склад по умолчанию (row, col левого верхнего угла).
     * (0, 0) — угол сетки с row=0, col=0; на изометрии это верхний угол ромба.
     */
    public static final int WAREHOUSE_ROW = 0;
    public static final int WAREHOUSE_COL = 0;

    private int size = DEFAULT_SIZE;
    private List<Cell> cells = new ArrayList<>(size * size);
    private List<PlacedObject> objects = new ArrayList<>();
    private long nextObjectId = 1L;

    public Board() {
        for (int i = 0; i < size * size; i++) {
            cells.add(new Cell(i));
        }
    }

    // ---------- доступ к клеткам ----------

    public int indexOf(int row, int col) {
        if (row < 0 || row >= size || col < 0 || col >= size) {
            throw new IllegalArgumentException("row/col out of bounds: " + row + "," + col);
        }
        return row * size + col;
    }

    public Cell cell(int index) {
        if (index < 0 || index >= cells.size()) {
            throw new IllegalArgumentException("cell index out of bounds: " + index);
        }
        return cells.get(index);
    }

    public Cell cell(int row, int col) {
        return cell(indexOf(row, col));
    }

    // ---------- проверка занятости ----------

    public boolean canPlace(int row, int col, int width, int height) {
        if (row < 0 || col < 0) return false;
        if (row + height > size || col + width > size) return false;

        for (int r = row; r < row + height; r++) {
            for (int c = col; c < col + width; c++) {
                Cell cell = cell(r, c);
                if (cell.getType() != Cell.Type.EMPTY) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---------- размещение ----------

    public PlacedObject placeCrop(int row, int col, int width, int height,
                                  String crop, int growSeconds) {
        if (!canPlace(row, col, width, height)) {
            throw new IllegalStateException("Cannot place crop at " + row + "," + col);
        }

        PlacedObject obj = new PlacedObject();
        obj.setId(nextObjectId++);
        obj.setKind(PlacedObject.Kind.CROP);
        obj.setRow(row);
        obj.setCol(col);
        obj.setWidth(width);
        obj.setHeight(height);
        obj.setCrop(crop);
        obj.setGrowSeconds(growSeconds);
        Instant now = Instant.now();
        obj.setPlantedAt(now);
        obj.setReadyAt(now.plusSeconds(growSeconds));

        occupy(row, col, width, height, obj.getId());
        objects.add(obj);
        return obj;
    }

    public PlacedObject placeBuilding(int row, int col, int width, int height,
                                      String building, int level) {
        if (!canPlace(row, col, width, height)) {
            throw new IllegalStateException("Cannot place building at " + row + "," + col);
        }

        PlacedObject obj = new PlacedObject();
        obj.setId(nextObjectId++);
        obj.setKind(PlacedObject.Kind.BUILDING);
        obj.setRow(row);
        obj.setCol(col);
        obj.setWidth(width);
        obj.setHeight(height);
        obj.setBuilding(building);
        obj.setLevel(level);
        obj.setBuiltAt(Instant.now());

        occupy(row, col, width, height, obj.getId());
        objects.add(obj);
        return obj;
    }

    private void occupy(int row, int col, int width, int height, Long objectId) {
        for (int r = row; r < row + height; r++) {
            for (int c = col; c < col + width; c++) {
                Cell cell = cell(r, c);
                cell.setType(Cell.Type.OCCUPIED);
                cell.setObjectId(objectId);
            }
        }
    }

    // ---------- удаление ----------

    public PlacedObject removeObject(long objectId) {
        Optional<PlacedObject> found = objects.stream()
                .filter(o -> o.getId() == objectId)
                .findFirst();
        if (found.isEmpty()) return null;

        PlacedObject obj = found.get();
        for (int r = obj.getRow(); r < obj.getRow() + obj.getHeight(); r++) {
            for (int c = obj.getCol(); c < obj.getCol() + obj.getWidth(); c++) {
                Cell cell = cell(r, c);
                cell.setType(Cell.Type.EMPTY);
                cell.setObjectId(null);
            }
        }
        objects.remove(obj);
        return obj;
    }

    public Optional<PlacedObject> findObject(long objectId) {
        return objects.stream().filter(o -> o.getId() == objectId).findFirst();
    }

    // ---------- сбор урожая ----------

    public PlacedObject harvest(long objectId) {
        PlacedObject obj = findObject(objectId)
                .orElseThrow(() -> new IllegalStateException("Object not found"));
        if (obj.getKind() != PlacedObject.Kind.CROP) {
            throw new IllegalStateException("Not a crop");
        }
        if (Instant.now().isBefore(obj.getReadyAt())) {
            throw new IllegalStateException("Not ready yet");
        }
        // урожай собран — культура остаётся на грядке и растёт заново
        Instant now = Instant.now();
        int grow;
        if (obj.getGrowSeconds() != null && obj.getGrowSeconds() > 0) {
            grow = obj.getGrowSeconds();
        } else if (obj.getPlantedAt() != null && obj.getReadyAt() != null) {
            grow = (int) Math.max(1, obj.getReadyAt().getEpochSecond() - obj.getPlantedAt().getEpochSecond());
        } else {
            grow = 60;
        }
        obj.setGrowSeconds(grow);
        obj.setPlantedAt(now);
        obj.setReadyAt(now.plusSeconds(grow));
        return obj;
    }

    public void resize(int newSize) {
        if (newSize <= size) return;
        size = newSize;
        cells = new ArrayList<>(size * size);
        for (int i = 0; i < size * size; i++) cells.add(new Cell(i));
        for (PlacedObject o : objects) {
            occupy(o.getRow(), o.getCol(), o.getWidth(), o.getHeight(), o.getId());
        }
    }

    // ---------- склад ----------

    /**
     * Гарантирует, что на поле есть склад нужного размера.
     * <ul>
     *   <li>склада нет — ставит его на место по умолчанию (левый верхний угол);</li>
     *   <li>склад есть, но размер устарел (например, 2×2) — один раз пересоздаёт его
     *       нужного размера на месте по умолчанию (содержимое склада хранится отдельно и не теряется);</li>
     *   <li>склад есть и размер верный — ничего не трогает (игрок мог его перенести).</li>
     * </ul>
     *
     * @return true, если доска изменилась
     */
    public boolean ensureWarehouse() {
        PlacedObject existing = null;
        for (PlacedObject o : objects) {
            if (o.getKind() == PlacedObject.Kind.BUILDING && WAREHOUSE.equals(o.getBuilding())) {
                existing = o;
                break;
            }
        }

        if (existing != null) {
            if (existing.getWidth() == WAREHOUSE_WIDTH && existing.getHeight() == WAREHOUSE_HEIGHT) {
                return false;
            }
            removeObject(existing.getId());
        }
        return placeWarehouse();
    }

    private boolean placeWarehouse() {
        final int w = WAREHOUSE_WIDTH, h = WAREHOUSE_HEIGHT;
        // {row, col}: сначала место по умолчанию, затем остальные углы
        int[][] spots = {
                {WAREHOUSE_ROW, WAREHOUSE_COL},
                {0, size - w},
                {size - h, 0},
                {size - h, size - w}
        };
        for (int[] s : spots) {
            if (canPlace(s[0], s[1], w, h)) {
                placeBuilding(s[0], s[1], w, h, WAREHOUSE, 1);
                return true;
            }
        }
        for (int r = 0; r <= size - h; r++) {
            for (int c = 0; c <= size - w; c++) {
                if (canPlace(r, c, w, h)) {
                    placeBuilding(r, c, w, h, WAREHOUSE, 1);
                    return true;
                }
            }
        }
        return false;
    }

    public PlacedObject moveObject(long objectId, int newRow, int newCol) {
        PlacedObject obj = findObject(objectId)
                .orElseThrow(() -> new IllegalStateException("Object not found"));
        if (newRow < 0 || newCol < 0
                || newRow + obj.getHeight() > size || newCol + obj.getWidth() > size) {
            throw new IllegalStateException("Out of bounds");
        }
        // освобождаем старые клетки
        for (int r = obj.getRow(); r < obj.getRow() + obj.getHeight(); r++)
            for (int c = obj.getCol(); c < obj.getCol() + obj.getWidth(); c++) {
                Cell cell = cell(r, c);
                cell.setType(Cell.Type.EMPTY);
                cell.setObjectId(null);
            }
        if (!canPlace(newRow, newCol, obj.getWidth(), obj.getHeight())) {
            occupy(obj.getRow(), obj.getCol(), obj.getWidth(), obj.getHeight(), obj.getId()); // откат
            throw new IllegalStateException("Place is occupied");
        }
        obj.setRow(newRow);
        obj.setCol(newCol);
        occupy(newRow, newCol, obj.getWidth(), obj.getHeight(), obj.getId());
        return obj;
    }

    // getters/setters
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public List<Cell> getCells() { return cells; }
    public void setCells(List<Cell> cells) { this.cells = cells; }
    public List<PlacedObject> getObjects() { return objects; }
    public void setObjects(List<PlacedObject> objects) { this.objects = objects; }
    public long getNextObjectId() { return nextObjectId; }
    public void setNextObjectId(long nextObjectId) { this.nextObjectId = nextObjectId; }
}