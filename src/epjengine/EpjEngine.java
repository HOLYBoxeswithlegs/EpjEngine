package epjengine;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.*;

import javax.imageio.ImageIO;

import org.lwjgl.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.*;
import org.lwjgl.util.glu.GLU;

public class EpjEngine {
    private int width = 1280;
    private int height = 720;
    private static final float RENDER_DISTANCE = 100000.0f;
    private float playerX = 0.0f;
    private float playerY = 0.0f; 
    private float playerZ = 5.0f;
    private float pitch = 0.0f;
    private float yaw = 0.0f;
    private boolean mouseCaptured = true;
    private boolean isJumping = false;
    // 0.54
    private float jumpHeight = 0.54f;
    private float gravity = -0.025f;
    private float verticalSpeed = 0.0f;
    private int wallTexture;
    private List<float[]> walls = new ArrayList<>();
    private final Random random = new Random();
    
    private long lastTime = System.currentTimeMillis();
    private int frames = 0;
    private int fps = 0;

    public void displayFPS() {
        frames++;
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastTime >= 1000) {
            fps = frames;
            frames = 0;
            lastTime = currentTime;
            System.out.println("FPS: " + fps);
        }
    }

    public void run() {
        init();
        generateWalls();
        loop();
        cleanUp();
    }

    private void init() {
        // Initialize Display
        try {
            Display.setDisplayMode(new DisplayMode(width, height));
            Display.setTitle("old bc3d renderer that i made");
            Display.setInitialBackground(0, 0, 0);
            Display.create();
        } catch (LWJGLException e) {
            e.printStackTrace();
            System.exit(0);
        }

        // Initialize OpenGL
        GL11.glClearColor(0.f, 0.f, 0.f, 1.0f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D); // Enable 2D Textures

        // Set up perspective projection
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        float fov = 70.0f; // Field of view
        float aspectRatio = (float) width / height;
        float nearPlane = 0.1f;
        float farPlane = RENDER_DISTANCE;

        // Set perspective projection matrix
        GLU.gluPerspective(fov, aspectRatio, nearPlane, farPlane);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        // Load wall texture
        wallTexture = loadTexture("brick.png");
    }

    private void generateWalls() {
        walls.clear();  // Clear previous walls

        int gridSize = 100; // Size of the grid (number of cells per row/column)
        float cellSize = 4.0f; // Distance between cells in the grid
        float wallWidth = 2.0f;
        float wallHeight = 5f;
        float wallDepth = 0.1f;
        float alignmentOffset = wallDepth / 2;  // Small offset to remove gaps

        for (int x = -gridSize / 2; x < gridSize / 2; x++) {
            for (int z = -gridSize / 2; z < gridSize / 2; z++) {
                boolean placeWall = false;
                float rotation = 0.0f;
                float adjustedX = x * cellSize;
                float adjustedZ = z * cellSize;

                // Create L and C shapes in a more structured manner
                if (random.nextInt(4) == 0) {
                    // L-shape
                    if (random.nextBoolean()) {
                        placeWall = true;
                        rotation = random.nextBoolean() ? 0.0f : 90.0f;
                    }
                } else if (random.nextInt(8) == 0) {
                    // C-shape
                    placeWall = true;
                    rotation = 90.0f;
                } else if (random.nextInt(10) == 0) {
                    // Single wall to break up space
                    placeWall = true;
                    rotation = random.nextBoolean() ? 0.0f : 90.0f;
                }

                if (placeWall) {
                    // Adjust wall position based on rotation to align with the grid
                    if (rotation == 90.0f) {
                        adjustedX += cellSize / 2 - alignmentOffset;
                    } else {
                        adjustedZ += cellSize / 2 - alignmentOffset;
                    }

                    float[] wall = new float[7];
                    wall[0] = adjustedX;
                    wall[1] = 0.0f;  // Y position stays at ground level
                    wall[2] = adjustedZ;
                    wall[3] = wallWidth;
                    wall[4] = wallHeight;
                    wall[5] = wallDepth;
                    wall[6] = rotation;

                    // Add wall to list
                    walls.add(wall);
                }
            }
        }
    }

    private void loop() {
        while (!Display.isCloseRequested()) {
            handleInput();
            updatePhysics();

            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glLoadIdentity();
            GL11.glRotatef(pitch, 1.0f, 0.0f, 0.0f);
            GL11.glRotatef(yaw, 0.0f, 1.0f, 0.0f);
            GL11.glTranslatef(-playerX, -playerY, -playerZ);

            renderWalls();
            displayFPS();
            Display.update();
            Display.sync(60);
        }
    }

    private void renderWalls() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, wallTexture);
        for (float[] wall : walls) {
            GL11.glPushMatrix();
            GL11.glTranslatef(wall[0], wall[1], wall[2]);
            GL11.glRotatef(wall[6], 0.0f, 1.0f, 0.0f);  // Apply rotation

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0f, 0.0f);
            GL11.glVertex3f(-wall[3], -wall[4], 0.0f);

            GL11.glTexCoord2f(1.0f, 0.0f);
            GL11.glVertex3f(wall[3], -wall[4], 0.0f);

            GL11.glTexCoord2f(1.0f, 1.0f);
            GL11.glVertex3f(wall[3], wall[4], 0.0f);

            GL11.glTexCoord2f(0.0f, 1.0f);
            GL11.glVertex3f(-wall[3], wall[4], 0.0f);
            GL11.glEnd();

            GL11.glPopMatrix();
        }
    }

    private boolean checkCollision(float newX, float newY, float newZ) {
        for (float[] wall : walls) {
            float wallX = wall[0];
            float wallY = wall[1];
            float wallZ = wall[2];
            float wallWidth = wall[3];
            float wallHeight = wall[4];
            float wallDepth = wall[5];
            float rotation = wall[6];

            // Adjust dimensions based on rotation (90 degrees swaps width and depth)
            float effectiveWidth = rotation == 90 ? wallDepth : wallWidth;
            float effectiveDepth = rotation == 90 ? wallWidth : wallDepth;

            // Check if the player's new position collides with the wall boundaries in 3D space
            boolean collisionX = newX > wallX - effectiveWidth && newX < wallX + effectiveWidth;
            boolean collisionY = newY > wallY - wallHeight && newY < wallY + wallHeight;
            boolean collisionZ = newZ > wallZ - effectiveDepth && newZ < wallZ + effectiveDepth;

            if (collisionX && collisionY && collisionZ) {
                return true;  // Collision detected
            }
        }
        return false;  // No collision
    }


    private void handleInput() {
        // Capture mouse
        if (mouseCaptured) {
            // Get mouse position
            int deltaX = Mouse.getDX();
            int deltaY = Mouse.getDY();

            // Update pitch and yaw based on mouse movement
            yaw += deltaX * 0.1f;
            pitch -= deltaY * 0.1f;

            // Clamp pitch
            if (pitch > 89.0f) pitch = 89.0f;
            if (pitch < -89.0f) pitch = -89.0f;

            // Hide the cursor
            Mouse.setGrabbed(true);
        } else {
            // Show the cursor
            Mouse.setGrabbed(false);
        }

        // Handle keyboard input
        float moveSpeed = 0.1f;

        // Calculate forward and right vectors
        float forwardX = (float) Math.sin(Math.toRadians(yaw));
        float forwardZ = (float) -Math.cos(Math.toRadians(yaw));
        float rightX = (float) Math.sin(Math.toRadians(yaw + 90));
        float rightZ = (float) -Math.cos(Math.toRadians(yaw + 90));

        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
        	moveSpeed = 0.2f;
        }
        
        if (Keyboard.isKeyDown(Keyboard.KEY_W)) {
            if (!checkCollision(playerX + forwardX * moveSpeed, playerY, playerZ + forwardZ * moveSpeed)) {
                playerX += forwardX * moveSpeed; // Move forward
                playerZ += forwardZ * moveSpeed;
            }
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_S)) {
            if (!checkCollision(playerX - forwardX * moveSpeed, playerY, playerZ - forwardZ * moveSpeed)) {
                playerX -= forwardX * moveSpeed; // Move backward
                playerZ -= forwardZ * moveSpeed;
            }
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_D)) {
            if (!checkCollision(playerX + rightX * moveSpeed, playerY, playerZ + rightZ * moveSpeed)) {
                playerX += rightX * moveSpeed; // Move left
                playerZ += rightZ * moveSpeed;
            }
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_A)) {
            if (!checkCollision(playerX - rightX * moveSpeed, playerY, playerZ - rightZ * moveSpeed)) {
                playerX -= rightX * moveSpeed; // Move right
                playerZ -= rightZ * moveSpeed;
            }
        }

        // Jumping logic
        if (Keyboard.isKeyDown(Keyboard.KEY_SPACE) && !isJumping) {
            isJumping = true; // Start jumping
            verticalSpeed = jumpHeight; // Set upward speed
        }

        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) {
            mouseCaptured = !mouseCaptured; // Toggle mouse capture
        }
    }

    private void updatePhysics() {
        if (isJumping) {
            verticalSpeed += gravity;
            playerY += verticalSpeed;
            if (playerY <= 0.0f) {
                playerY = 0.0f;
                isJumping = false;
                verticalSpeed = 0.0f;
            }
        }
    }

    private int loadTexture(String filePath) {
        BufferedImage image;
        try {
            // Load the image
            image = ImageIO.read(new File(filePath));

            // Create a new texture ID
            int textureID = GL11.glGenTextures();

            // Bind the texture
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureID);

            // Set texture parameters
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_CLAMP_TO_EDGE, GL12.GL_CLAMP_TO_EDGE);

            // Convert the image to a format suitable for OpenGL
            int[] pixels = new int[image.getWidth() * image.getHeight()];
            image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());

            // Create a buffer for the texture
            ByteBuffer buffer = ByteBuffer.allocateDirect(image.getWidth() * image.getHeight() * 4);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = pixels[y * image.getWidth() + x];
                    buffer.put((byte) ((pixel >> 16) & 0xFF)); // Red
                    buffer.put((byte) ((pixel >> 8) & 0xFF));  // Green
                    buffer.put((byte) (pixel & 0xFF));         // Blue
                    buffer.put((byte) ((pixel >> 24) & 0xFF)); // Alpha
                }
            }
            buffer.flip(); // Flip the buffer for reading

            // Upload the texture to OpenGL
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, image.getWidth(), image.getHeight(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

            return textureID; // Return the texture ID
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1; // Return -1 on failure
    }

    private void cleanUp() {
        Display.destroy();
    }

    public static void main(String[] args) {
        new EpjEngine().run();
    }
}
