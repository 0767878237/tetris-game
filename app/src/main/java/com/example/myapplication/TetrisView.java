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

import androidx.annotation.Nullable;

import java.util.Random;

public class TetrisView extends View {
    private static final int BOARD_WIDTH = 10;
    private static final int BOARD_HEIGHT = 20;
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
    private RectF retryButton;

    private Paint blockPaint, boardPaint, shadowPaint, textPaint;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Random random = new Random();
    private boolean isGameOver = false;
    private int score = 0;
    private float touchStartX, touchStartY;
    private RectF leftButton, rightButton, rotateButton, downButton;

    public TetrisView(Context context) {
        super(context);
        init();
    }

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
        shadowPaint.setColor(Color.parseColor("#40000000"));
        shadowPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setAntiAlias(true);

        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();

        startNewGame();
        handler.postDelayed(gameLoop, 1000);
    }

    private void startNewGame() {
        board = new int[BOARD_HEIGHT][BOARD_WIDTH];
        spawnShape();
        isGameOver = false;
        score = 0;
        invalidate();
        requestFocus(); // Đảm bảo view có focus để nhận sự kiện phím (nếu có)

        // Khởi động lại gameLoop
        handler.removeCallbacks(gameLoop); // Xóa các lần gọi gameLoop cũ (đề phòng)
        handler.postDelayed(gameLoop, 1000); // Lên lịch chạy gameLoop sau 1 giây
    }

    private void spawnShape() {
        shapeType = random.nextInt(SHAPES.length);
        currentShape = SHAPES[shapeType];
        shapeX = BOARD_WIDTH / 2 - currentShape[0].length / 2;
        shapeY = 0;
        if (!canPlaceShape()) {
            isGameOver = true;
        }
    }

    private boolean canPlaceShape() {
        for (int y = 0; y < currentShape.length; y++) {
            for (int x = 0; x < currentShape[y].length; x++) {
                if (currentShape[y][x] == 1) {
                    int boardX = shapeX + x;
                    int boardY = shapeY + y;
                    if (boardX < 0 || boardX >= BOARD_WIDTH || boardY >= BOARD_HEIGHT ||
                            (boardY >= 0 && board[boardY][boardX] != 0)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void mergeShape() {
        for (int y = 0; y < currentShape.length; y++) {
            for (int x = 0; x < currentShape[y].length; x++) {
                if (currentShape[y][x] == 1) {
                    int boardY = shapeY + y;
                    if (boardY >= 0) {
                        board[boardY][shapeX + x] = shapeType + 1;
                    }
                }
            }
        }
    }

    private void clearLines() {
        int linesCleared = 0;
        for (int y = BOARD_HEIGHT - 1; y >= 0; y--) {
            boolean full = true;
            for (int x = 0; x < BOARD_WIDTH; x++) {
                if (board[y][x] == 0) {
                    full = false;
                    break;
                }
            }
            if (full) {
                linesCleared++;
                for (int yy = y; yy > 0; yy--) {
                    board[yy] = board[yy - 1].clone();
                }
                board[0] = new int[BOARD_WIDTH];
                y++;
            }
        }
        score += linesCleared * 100;
    }

    private void moveDown() {
        shapeY++;
        if (!canPlaceShape()) {
            shapeY--;
            mergeShape();
            clearLines();
            spawnShape();
        }
        invalidate();
    }

    private void moveLeft() {
        shapeX--;
        if (!canPlaceShape()) {
            shapeX++;
        }
        invalidate();
    }

    private void moveRight() {
        shapeX++;
        if (!canPlaceShape()) {
            shapeX--;
        }
        invalidate();
    }

    private void rotate() {
        int[][] rotated = new int[currentShape[0].length][currentShape.length];
        for (int y = 0; y < currentShape.length; y++) {
            for (int x = 0; x < currentShape[y].length; x++) {
                rotated[x][currentShape.length - 1 - y] = currentShape[y][x];
            }
        }

        int oldX = shapeX;
        int[][] oldShape = currentShape;
        currentShape = rotated;

        if (canPlaceShape()) {
            // OK, không cần dịch
        } else if (tryKick(-1)) {
            shapeX -= 1;
        } else if (tryKick(1)) {
            shapeX += 1;
        } else if (tryKick(-2)) {
            shapeX -= 2;
        } else if (tryKick(2)) {
            shapeX += 2;
        } else {
            // Không xoay được
            currentShape = oldShape;
            shapeX = oldX;
        }
        invalidate();
    }

    private boolean tryKick(int dx) {
        shapeX += dx;
        boolean can = canPlaceShape();
        shapeX -= dx;
        return can;
    }


    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            if (!isGameOver) {
                moveDown();
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.parseColor("#1E1E1E"));

        int left = 50;
        int top = 50;
        int boardRight = left + BOARD_WIDTH * blockSize;
        int boardBottom = top + BOARD_HEIGHT * blockSize;

        canvas.drawRect(left, top, boardRight, boardBottom, shadowPaint);
        canvas.drawRect(left, top, boardRight, boardBottom, boardPaint);

        for (int y = 0; y < BOARD_HEIGHT; y++) {
            for (int x = 0; x < BOARD_WIDTH; x++) {
                if (board[y][x] > 0) {
                    float bx = left + x * blockSize;
                    float by = top + y * blockSize;
                    blockPaint.setColor(COLORS[board[y][x] - 1]);
                    canvas.drawRect(bx + 2, by + 2, bx + blockSize - 2, by + blockSize - 2, blockPaint);
                }
            }
        }

        for (int y = 0; y < currentShape.length; y++) {
            for (int x = 0; x < currentShape[y].length; x++) {
                if (currentShape[y][x] == 1) {
                    float bx = left + (shapeX + x) * blockSize;
                    float by = top + (shapeY + y) * blockSize;
                    blockPaint.setColor(COLORS[shapeType]);
                    canvas.drawRect(bx + 2, by + 2, bx + blockSize - 2, by + blockSize - 2, blockPaint);
                }
            }
        }

        canvas.drawText("Score: " + score, left, boardBottom + blockSize, textPaint);

        float btnTop = boardBottom + blockSize * 2;
        float btnSize = blockSize * 2;

        leftButton = new RectF(left, btnTop, left + btnSize, btnTop + btnSize);
        rightButton = new RectF(left + btnSize + 20, btnTop, left + 2 * btnSize + 20, btnTop + btnSize);
        rotateButton = new RectF(left + 2 * (btnSize + 20), btnTop, left + 3 * btnSize + 40, btnTop + btnSize);
        downButton = new RectF(left + 3 * (btnSize + 20), btnTop, left + 4 * btnSize + 60, btnTop + btnSize);

        blockPaint.setColor(Color.parseColor("#4CAF50"));
        canvas.drawRoundRect(leftButton, 20, 20, blockPaint);
        canvas.drawRoundRect(rightButton, 20, 20, blockPaint);
        canvas.drawRoundRect(rotateButton, 20, 20, blockPaint);
        canvas.drawRoundRect(downButton, 20, 20, blockPaint);

        textPaint.setTextSize(blockSize);
        canvas.drawText("←", left + btnSize / 3, btnTop + btnSize * 2 / 3, textPaint);
        canvas.drawText("→", rightButton.left + btnSize / 3, btnTop + btnSize * 2 / 3, textPaint);
        canvas.drawText("↻", rotateButton.left + btnSize / 3, btnTop + btnSize * 2 / 3, textPaint);
        canvas.drawText("↓", downButton.left + btnSize / 3, btnTop + btnSize * 2 / 3, textPaint);

        if (isGameOver) {
            blockPaint.setColor(Color.parseColor("#AA000000"));
            canvas.drawRect(0, 0, getWidth(), getHeight(), blockPaint);

            textPaint.setTextSize(blockSize * 1.5f);
            canvas.drawText("Game Over", getWidth() / 2f - blockSize * 3, getHeight() / 2f - blockSize, textPaint);

            float btnWidth = blockSize * 5;
            float btnHeight = blockSize * 2;
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f + blockSize * 2;

            retryButton = new RectF(cx - btnWidth / 2, cy - btnHeight / 2, cx + btnWidth / 2, cy + btnHeight / 2);
            blockPaint.setColor(Color.parseColor("#FF5722"));
            canvas.drawRoundRect(retryButton, 30, 30, blockPaint);

            textPaint.setTextSize(blockSize);
            textPaint.setColor(Color.WHITE);
            canvas.drawText("Chơi lại", cx - blockSize * 1.5f, cy + blockSize / 3, textPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        if (isGameOver && event.getAction() == MotionEvent.ACTION_UP) {
            if (retryButton != null && retryButton.contains(x, y)) {
                startNewGame();
                return true;
            }
        }

        if (isGameOver) return true;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = x;
                touchStartY = y;
                break;
            case MotionEvent.ACTION_UP:
                if (leftButton.contains(x, y)) moveLeft();
                else if (rightButton.contains(x, y)) moveRight();
                else if (rotateButton.contains(x, y)) rotate();
                else if (downButton.contains(x, y)) moveDown();
                else if (Math.abs(x - touchStartX) > Math.abs(y - touchStartY)) {
                    if (x - touchStartX > 100) moveRight();
                    else if (touchStartX - x > 100) moveLeft();
                } else {
                    if (y - touchStartY > 100) moveDown();
                    else if (touchStartY - y > 100) rotate();
                }
                break;
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isGameOver) return super.onKeyDown(keyCode, event);

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
        blockSize = Math.min((w - 100) / BOARD_WIDTH, (h - 600) / BOARD_HEIGHT);
        textPaint.setTextSize(blockSize);
    }
}
