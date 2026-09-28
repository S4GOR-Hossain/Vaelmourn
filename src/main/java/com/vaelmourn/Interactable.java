package com.vaelmourn;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

/** Objects that can be interacted with (chests, NPCs, etc.) via the F key when nearby. */
public interface Interactable {

    Vector3f getPosition();

    boolean isInRange(Vector3f playerPos, float range);

    /** Called when the player presses F while in range of this object. */
    void interact();

    Node getNode();

    void cleanup();
}
