package com.vaelmourn;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

/**
 * Interface for objects that can be interacted with (chests, NPCs, etc.)
 * Interactable objects can be triggered via the F key when the player is nearby.
 */
public interface Interactable {

    /**
     * Get the position of this interactable object.
     */
    Vector3f getPosition();

    /**
     * Check if the player is close enough to interact with this object.
     */
    boolean isInRange(Vector3f playerPos, float range);

    /**
     * Called when the player presses F while in range of this object.
     */
    void interact();

    /**
     * Get the scene node for this interactable (for rendering/cleanup).
     */
    Node getNode();

    /**
     * Clean up resources.
     */
    void cleanup();
}
