package epjengine;

import org.lwjgl.*;
import org.lwjgl.opengl.*;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.input.*;
import java.nio.ByteBuffer;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class EpjEngine {

    private int width = 800;
    private int height = 600;

    private float playerX = 0.0f;
    private float playerY = 0.0f; // Y coordinate (height)
    private float playerZ = 5.0f;
    private float pitch = 0.0f;
    private float yaw = 0.0f;

    private boolean mouseCaptured = true;

    // Jumping variables
    private boolean isJumping = false;
    private float jumpHeight = 0.54f; // Maximum jump height
    private float gravity = -0.025f; // Gravity force
    private float verticalSpeed = 0.0f; // Current vertical speed

    // Texture ID
    private int wallTexture;

    // Define wall positions (centerX, centerY, centerZ, width, height, depth)
    private final float[][] walls = {
        {0.0f, 0.0f, -3.0f, 4.0f, 2.0f, 0.1f},  // Back Wall
        {0.0f, 0.0f, -6.0f, 4.0f, 2.0f, 0.1f},  // Back Wall
        {0.0f, 0.0f, -9.0f, 4.0f, 2.0f, 0.1f},  // Back Wall
    };

    public void run() {
        init();
        loop();
        cleanUp();
    }

    private void init() {
        // Initialize Display
        try {
            Display.setDisplayMode(new DisplayMode(width, height));
            Display.setTitle("Epj Engine");
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
        float farPlane = 100.0f;

        // Set perspective projection matrix
        GLU.gluPerspective(fov, aspectRatio, nearPlane, farPlane);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        // Load wall texture
        wallTexture = loadTexture("brick.png");
    }

    private void loop() {
        while (!Display.isCloseRequested()) {
            handleInput();
            updatePhysics(); // Update physics for jumping

            // Clear the screen
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            // Set the camera
            GL11.glLoadIdentity();
            GL11.glRotatef(pitch, 1.0f, 0.0f, 0.0f);
            GL11.glRotatef(yaw, 0.0f, 1.0f, 0.0f);
            GL11.glTranslatef(-playerX, -playerY, -playerZ);

            // Render walls
            renderWalls();

            // Update Display
            Display.update();
            Display.sync(60); // Cap to 60 FPS
        }
    }

    private void renderWalls() {
        // Bind the texture for the wall
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, wallTexture);
        for (float[] wall : walls) {
            GL11.glPushMatrix();
            GL11.glTranslatef(wall[0], wall[1], wall[2]);
            GL11.glBegin(GL11.GL_QUADS);
            
            // Specify texture coordinates and vertices for each corner
            GL11.glTexCoord2f(0.0f, 0.0f); // Bottom-left
            GL11.glVertex3f(-wall[3], -wall[4], 0.0f);

            GL11.glTexCoord2f(1.0f, 0.0f); // Bottom-right
            GL11.glVertex3f(wall[3], -wall[4], 0.0f);

            GL11.glTexCoord2f(1.0f, 1.0f); // Top-right
            GL11.glVertex3f(wall[3], wall[4], 0.0f);

            GL11.glTexCoord2f(0.0f, 1.0f); // Top-left
            GL11.glVertex3f(-wall[3], wall[4], 0.0f);

            GL11.glEnd();
            GL11.glPopMatrix();
        }
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
            // Apply gravity
            verticalSpeed += gravity;
            playerY += verticalSpeed;

            // Check if player has landed
            if (playerY <= 0.0f) { // Assume ground is at Y = 0
                playerY = 0.0f; // Reset to ground level
                isJumping = false; // Stop jumping
                verticalSpeed = 0.0f; // Reset vertical speed
            }
        }
    }

    private boolean checkCollision(float newX, float newY, float newZ) {
        for (float[] wall : walls) {
            if (newX > wall[0] - wall[3] && newX < wall[0] + wall[3] &&
                newY > wall[1] - wall[4] && newY < wall[1] + wall[4] &&
                newZ > wall[2] - wall[5] && newZ < wall[2] + wall[5]) {
                return true; // Collision detected
            }
        }
        return false; // No collision
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
