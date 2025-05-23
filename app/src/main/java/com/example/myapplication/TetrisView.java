package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.graphics.LinearGradient;
import android.graphics.Shader;

import androidx.annotation.Nullable;

import java.util.Random;

public class TetrisView extends View {
    // Member variables for UI elements to avoid repeated allocations in onDraw
    private RectF startButton;
    private RectF retryButton;
    private RectF leftButton;
    private RectF rightButton;
    private RectF rotateButton;
    private RectF downButton;

    // Member variables for drawing coordinates, calculated in onSizeChanged
    private float cx, cy; // Center X and Y for start/retry buttons
    private float retryCx, retryCy; // Separate center for retry button if different
    private int left, top, boardRight, boardBottom; // Coordinates for the game board

    private static final int BOARD_WIDTH = 10;
    private static final int BOARD_HEIGHT = 20;
    private boolean isGameOver = false;
    private boolean isStartScreen = true;

    private static final int[][][] SHAPES = {
            {{1, 1, 1, 1}}, // I
            {{1, 1}, {1, 1}}, // O
            {{1, 1, 1}, {0, 1, 0}}, // T
            {{1, 1, 1}, {1, 0, 0}}, // L
            {{1, 1, 1}, {0, 0, 1}}, // J
            {{1, 1, 0}, {0, 1, 1}}, // S
            {{0, 1, 1}, {1, 1, 0}}  // Z
    };
    private static final int[] COLORS = {
            Color.CYAN, Color.YELLOW, Color.MAGENTA, Color.parseColor("#FFA500"),
            Color.BLUE, Color.GREEN, Color.RED
    };

    private int blockSize;
    private int[][] board = new int[BOARD_HEIGHT][BOARD_WIDTH];
    private int[][] currentShape;
    private int shapeX, shapeY, shapeType;

    private Paint blockPaint, boardPaint, shadowPaint, textPaint;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private int score = 0;
    private float touchStartX, touchStartY;
    private final long dropDelay = 1000; // Ban đầu khối rơi mỗi 1000ms
    // private long startTime; // This variable is not used, can be removed if not planned for future use

    // Constructor for programmatic creation (used by MainActivity.java)
    public TetrisView(Context context) {
        super(context);
        init();
    }

    // Constructor for XML inflation
    public TetrisView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        blockPaint = new Paint();
        boardPaint = new Paint();
        boardPaint.setColor(Color.parseColor("#333333"));
        boardPaint.setStyle(Paint.Style.FILL);

        shadowPaint = new Paint();
        shadowPaint.setColor(Color.parseColor("#40000000")); // Semi-transparent black for shadow
        shadowPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setAntiAlias(true);

        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();

        startNewGame();
    }

    private void startNewGame() {
        board = new int[BOARD_HEIGHT][BOARD_WIDTH]; // Clear the board
        spawnShape();
        isGameOver = false;
        isStartScreen = true; // Set to true to show start screen initially
        score = 0;
        invalidate(); // Redraw the view
        requestFocus(); // Ensure view has focus for key events

        // Stop any existing game loop and start a new one
        handler.removeCallbacks(gameLoop);
        handler.postDelayed(gameLoop, dropDelay); // Start the game loop after initial delay
    }

    private void spawnShape() {
        shapeType = random.nextInt(SHAPES.length); // Randomly select a shape type
        currentShape = SHAPES[shapeType]; // Get the shape's block configuration
        shapeX = BOARD_WIDTH / 2 - currentShape[0].length / 2; // Center the shape horizontally
        shapeY = 0; // Start at the top of the board

        // Check if the new shape can be placed, if not, game over
        if (!canPlaceShape()) {
            isGameOver = true;
            handler.removeCallbacks(gameLoop); // Stop the game loop if game over
        }
    }

    private boolean canPlaceShape() {
        for (int y = 0; y < currentShape.length; y++) {
            for (int x = 0; x < currentShape[y].length; x++) {
                if (currentShape[y][x] == 1) { // If it's a block of the current shape
                    int boardX = shapeX + x;
                    int boardY = shapeY + y;

                    // Check boundaries and collision with existing blocks on the board
                    if (boardX < 0 || boardX >= BOARD_WIDTH || boardY >= BOARD_HEIGHT ||
                            (boardY >= 0 && board[boardY][boardX] != 0)) {
                        return false; // Cannot place the shape here
                    }
                }
            }
        }
        return true; // Can place the shape
    }

    private void mergeShape() {
        // Merge the current shape into the board
        for (int y = 0; y < currentShape.length; y++) {
            for (int x = 0; x < currentShape[y].length; x++) {
                if (currentShape[y][x] == 1) {
                    int boardY = shapeY + y;
                    if (boardY >= 0) { // Only merge if within board boundaries (can be negative during initial spawn)
                        board[boardY][shapeX + x] = shapeType + 1; // Store shape type + 1 (0 is empty)
                    }
                }
            }
        }
    }

    private void clearLines() {
        int linesCleared = 0;
        // Iterate from bottom to top to check for full lines
        for (int y = BOARD_HEIGHT - 1; y >= 0; y--) {
            boolean full = true;
            for (int x = 0; x < BOARD_WIDTH; x++) {
                if (board[y][x] == 0) { // If any block is empty, line is not full
                    full = false;
                    break;
                }
            }
            if (full) {
                linesCleared++;
                // Shift all lines above down by one
                for (int yy = y; yy > 0; yy--) {
                    board[yy] = board[yy - 1].clone();
                }
                board[0] = new int[BOARD_WIDTH]; // Clear the top line
                y++; // Re-check the current line (which now contains the line above)
            }
        }
        score += linesCleared * 100; // Update score based on lines cleared
    }

    private void moveDown() {
        shapeY++; // Move shape down by one unit
        if (!canPlaceShape()) { // If it can't be placed after moving down
            shapeY--; // Move it back up
            mergeShape(); // Merge it with the board
            clearLines(); // Check and clear any full lines
            spawnShape(); // Spawn a new shape
        }
        invalidate(); // Request a redraw
    }

    private void moveLeft() {
        shapeX--; // Move shape left by one unit
        if (!canPlaceShape()) { // If it can't be placed after moving left
            shapeX++; // Move it back right
        }
        invalidate(); // Request a redraw
    }

    private void moveRight() {
        shapeX++; // Move shape right by one unit
        if (!canPlaceShape()) { // If it can't be placed after moving right
            shapeX--; // Move it back left
        }
        invalidate(); // Request a redraw
    }

    private void rotate() {
        // Create a new array for the rotated shape
        int[][] rotated = new int[currentShape[0].length][currentShape.length];
        for (int y = 0; y < currentShape.length; y++) {
            for (int x = 0; x < currentShape[y].length; x++) {
                rotated[x][currentShape.length - 1 - y] = currentShape[y][x];
            }
        }

        int oldX = shapeX; // Store old X position for kickback logic
        int[][] oldShape = currentShape; // Store old shape for rollback
        currentShape = rotated; // Temporarily set to rotated shape

        if (canPlaceShape()) {
            // Rotation is successful, no kick needed
        } else if (tryKick(-1)) { // Try kicking left by 1
            shapeX -= 1;
        } else if (tryKick(1)) { // Try kicking right by 1
            shapeX += 1;
        } else if (tryKick(-2)) { // Try kicking left by 2
            shapeX -= 2;
        } else if (tryKick(2)) { // Try kicking right by 2
            shapeX += 2;
        } else {
            // Cannot rotate, revert to old shape and position
            currentShape = oldShape;
            shapeX = oldX;
        }
        invalidate(); // Request a redraw
    }

    private boolean tryKick(int dx) {
        shapeX += dx; // Apply the kick displacement
        boolean can = canPlaceShape(); // Check if the shape can be placed
        shapeX -= dx; // Revert the kick displacement
        return can; // Return true if the kick made it placeable
    }

    // The game loop, responsible for automatically moving the shape down
    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            if (!isGameOver) {
                moveDown(); // Move the current shape down
                handler.postDelayed(this, dropDelay); // Schedule next drop
            }
        }
    };

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.parseColor("#1E1E1E"));

        Paint backgroundPaint = new Paint();
        LinearGradient gradient = new LinearGradient(
                0, 0, 0, getHeight(),
                Color.parseColor("#1E1E1E"),
                Color.parseColor("#3E3E3E"),
                Shader.TileMode.CLAMP
        );
        backgroundPaint.setShader(gradient);
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);

        Paint gridPaint = new Paint();
        gridPaint.setColor(Color.parseColor("#2A2A2A"));
        gridPaint.setStrokeWidth(1);

        if (isStartScreen) {
            // Draw start screen elements
            textPaint.setTextSize(blockSize * 1.5f);
            textPaint.setColor(Color.WHITE);
            canvas.drawText("Tetris", getWidth() / 2f - blockSize * 2, getHeight() / 3f, textPaint);

            blockPaint.setColor(Color.parseColor("#FF9800")); // Orange color for button
            if (startButton != null) { // Null check for safety
                canvas.drawRoundRect(startButton, 30, 30, blockPaint);
            }

            textPaint.setTextSize(blockSize);
            textPaint.setColor(Color.BLACK);
            // Use member variables cx and cy for text positioning
            canvas.drawText("Bắt đầu chơi", cx - blockSize * 2.5f, cy + blockSize / 3f, textPaint);
            return; // Stop drawing here if it's the start screen
        }

        // Draw the game board background and shadow
        canvas.drawRect(left, top, boardRight, boardBottom, shadowPaint);
        canvas.drawRect(left, top, boardRight, boardBottom, boardPaint);

        // Draw existing blocks on the board
        for (int y = 0; y < BOARD_HEIGHT; y++) {
            for (int x = 0; x < BOARD_WIDTH; x++) {
                if (board[y][x] > 0) {
                    float bx = left + x * blockSize;
                    float by = top + y * blockSize;
                    blockPaint.setColor(COLORS[board[y][x] - 1]); // Get color based on block type
                    canvas.drawRect(bx + 2, by + 2, bx + blockSize - 2, by + blockSize - 2, blockPaint); // Draw with slight padding
                }
            }
        }

        // Draw the current falling shape
        for (int y = 0; y < currentShape.length; y++) {
            for (int x = 0; x < currentShape[y].length; x++) {
                if (currentShape[y][x] == 1) {
                    float bx = left + (shapeX + x) * blockSize;
                    float by = top + (shapeY + y) * blockSize;
                    blockPaint.setColor(COLORS[shapeType]); // Use current shape's color
                    canvas.drawRect(bx + 2, by + 2, bx + blockSize - 2, by + blockSize - 2, blockPaint); // Draw with slight padding
                }
            }
        }

        // Draw score text
        textPaint.setColor(Color.WHITE);
        canvas.drawText("Score: " + score, left, boardBottom + blockSize, textPaint);

        // Draw control buttons
        blockPaint.setColor(Color.parseColor("#4CAF50")); // Green color for buttons
        if (leftButton != null) canvas.drawRoundRect(leftButton, 20, 20, blockPaint);
        if (rightButton != null) canvas.drawRoundRect(rightButton, 20, 20, blockPaint);
        if (rotateButton != null) canvas.drawRoundRect(rotateButton, 20, 20, blockPaint);
        if (downButton != null) canvas.drawRoundRect(downButton, 20, 20, blockPaint);

        // Draw button icons/text (using float for division)
        textPaint.setTextSize(blockSize);
        if (leftButton != null) canvas.drawText("←", leftButton.left + leftButton.width() / 3f, leftButton.top + leftButton.height() * 2 / 3f, textPaint);
        if (rightButton != null) canvas.drawText("→", rightButton.left + rightButton.width() / 3f, rightButton.top + rightButton.height() * 2 / 3f, textPaint);
        if (rotateButton != null) canvas.drawText("↻", rotateButton.left + rotateButton.width() / 3f, rotateButton.top + rotateButton.height() * 2 / 3f, textPaint);
        if (downButton != null) canvas.drawText("↓", downButton.left + downButton.width() / 3f, downButton.top + downButton.height() * 2 / 3f, textPaint);


        if (isGameOver) {
            // Draw game over overlay
            blockPaint.setColor(Color.parseColor("#AA000000")); // Semi-transparent black overlay
            canvas.drawRect(0, 0, getWidth(), getHeight(), blockPaint);

            textPaint.setTextSize(blockSize * 1.5f);
            canvas.drawText("Game Over", getWidth() / 2f - blockSize * 3, getHeight() / 2f - blockSize, textPaint);

            blockPaint.setColor(Color.parseColor("#FF5722")); // Red color for retry button
            if (retryButton != null) { // Null check for safety
                canvas.drawRoundRect(retryButton, 30, 30, blockPaint);
            }

            textPaint.setTextSize(blockSize);
            textPaint.setColor(Color.WHITE);
            // Use member variables retryCx and retryCy for text positioning
            canvas.drawText("Chơi lại", retryCx - blockSize * 1.5f, retryCy + blockSize / 3f, textPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        // Handle start screen touch
        if (isStartScreen && event.getAction() == MotionEvent.ACTION_UP) {
            if (startButton != null && startButton.contains(x, y)) {
                isStartScreen = false;
                handler.postDelayed(gameLoop, dropDelay); // Start game loop on button press
                invalidate();
                performClick(); // For accessibility
                return true;
            }
        }

        // Handle game over retry button touch
        if (isGameOver && event.getAction() == MotionEvent.ACTION_UP) {
            if (retryButton != null && retryButton.contains(x, y)) {
                startNewGame(); // Restart the game
                performClick(); // For accessibility
                return true;
            }
        }

        // If game is over or on start screen, do not process game controls
        if (isGameOver || isStartScreen) return true;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = x;
                touchStartY = y;
                break;
            case MotionEvent.ACTION_UP:
                boolean handled = false;
                // Check if control buttons were pressed
                if (leftButton != null && leftButton.contains(x, y)) {
                    moveLeft();
                    handled = true;
                } else if (rightButton != null && rightButton.contains(x, y)) {
                    moveRight();
                    handled = true;
                } else if (rotateButton != null && rotateButton.contains(x, y)) {
                    rotate();
                    handled = true;
                } else if (downButton != null && downButton.contains(x, y)) {
                    moveDown();
                    handled = true;
                }
                // Check for swipe gestures if no button was pressed
                else if (Math.abs(x - touchStartX) > Math.abs(y - touchStartY)) { // Horizontal swipe
                    if (x - touchStartX > 100) { // Swipe right
                        moveRight();
                        handled = true;
                    } else if (touchStartX - x > 100) { // Swipe left
                        moveLeft();
                        handled = true;
                    }
                } else { // Vertical swipe
                    if (y - touchStartY > 100) { // Swipe down
                        moveDown();
                        handled = true;
                    } else if (touchStartY - y > 100) { // Swipe up (for rotation)
                        rotate();
                        handled = true;
                    }
                }
                if (handled) {
                    performClick(); // Call performClick if any action was performed
                }
                break;
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isGameOver || isStartScreen) return super.onKeyDown(keyCode, event); // Do not process key events if game is over or on start screen

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                moveLeft();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                moveRight();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                moveDown();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                rotate();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Calculate blockSize based on view dimensions
        blockSize = Math.min((w - 100) / BOARD_WIDTH, (h - 600) / BOARD_HEIGHT);
        textPaint.setTextSize(blockSize);

        // Calculate and initialize RectF objects and drawing coordinates here
        // Start Button
        float btnWidth = blockSize * 8;
        float btnHeight = blockSize * 2;
        cx = getWidth() / 2f;
        cy = getHeight() / 2f + blockSize * 2;
        startButton = new RectF(cx - btnWidth / 2, cy - btnHeight / 2, cx + btnWidth / 2, cy + btnHeight / 2);

        // Retry Button (assuming same position as start button for simplicity)
        float retryBtnWidth = blockSize * 5;
        float retryBtnHeight = blockSize * 2;
        retryCx = getWidth() / 2f; // Can be different if you want
        retryCy = getHeight() / 2f + blockSize * 2; // Can be different if you want
        retryButton = new RectF(retryCx - retryBtnWidth / 2, retryCy - retryBtnHeight / 2, retryCx + retryBtnWidth / 2, retryCy + retryBtnHeight / 2);

        // Game Board dimensions
        left = 50;
        top = 50;
        boardRight = left + BOARD_WIDTH * blockSize;
        boardBottom = top + BOARD_HEIGHT * blockSize;

        // Control Buttons
        float btnTop = boardBottom + blockSize * 2;
        float btnSize = blockSize * 2;
        // Initialize RectF objects for control buttons
        leftButton = new RectF(left, btnTop, left + btnSize, btnTop + btnSize);
        rightButton = new RectF(left + btnSize + 20, btnTop, left + 2 * btnSize + 20, btnTop + btnSize);
        rotateButton = new RectF(left + 2 * (btnSize + 20), btnTop, left + 3 * btnSize + 40, btnTop + btnSize);
        downButton = new RectF(left + 3 * (btnSize + 20), btnTop, left + 4 * btnSize + 60, btnTop + btnSize);
    }
}
