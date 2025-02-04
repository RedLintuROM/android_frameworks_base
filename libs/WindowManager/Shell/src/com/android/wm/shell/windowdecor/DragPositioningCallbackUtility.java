/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.wm.shell.windowdecor;

import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_BOTTOM;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_LEFT;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_RIGHT;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_TOP;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_UNDEFINED;

import android.graphics.PointF;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.SurfaceControl;

import com.android.wm.shell.common.DisplayController;

/**
 * Utility class that contains logic common to classes implementing {@link DragPositioningCallback}
 * Specifically, this class contains logic for determining changed bounds from a drag input
 * and applying that change to the task bounds when applicable.
 */
public class DragPositioningCallbackUtility {

    /**
     * Determine the delta between input's current point and the input start point.
     * @param inputX current input x coordinate
     * @param inputY current input y coordinate
     * @param repositionStartPoint initial input coordinate
     * @return delta between these two points
     */
    static PointF calculateDelta(float inputX, float inputY, PointF repositionStartPoint) {
        final float deltaX = inputX - repositionStartPoint.x;
        final float deltaY = inputY - repositionStartPoint.y;
        return new PointF(deltaX, deltaY);
    }

    /**
     * Based on type of resize and delta provided, calculate the new bounds to display for this
     * task.
     * @param ctrlType type of drag being performed
     * @param repositionTaskBounds the bounds the task is being repositioned to
     * @param taskBoundsAtDragStart the bounds of the task on the first drag input event
     * @param stableBounds bounds that represent the resize limit of this task
     * @param delta difference between start input and current input in x/y coordinates
     * @param displayController task's display controller
     * @param windowDecoration window decoration of the task being dragged
     * @return whether this method changed repositionTaskBounds
     */
    static boolean changeBounds(int ctrlType, Rect repositionTaskBounds, Rect taskBoundsAtDragStart,
            Rect stableBounds, PointF delta, DisplayController displayController,
            WindowDecoration windowDecoration) {
        // If task is being dragged rather than resized, return since this method only handles
        // with resizing
        if (ctrlType == CTRL_TYPE_UNDEFINED) {
            return false;
        }

        final int oldLeft = repositionTaskBounds.left;
        final int oldTop = repositionTaskBounds.top;
        final int oldRight = repositionTaskBounds.right;
        final int oldBottom = repositionTaskBounds.bottom;


        repositionTaskBounds.set(taskBoundsAtDragStart);

        // Make sure the new resizing destination in any direction falls within the stable bounds.
        // If not, set the bounds back to the old location that was valid to avoid conflicts with
        // some regions such as the gesture area.
        if ((ctrlType & CTRL_TYPE_LEFT) != 0) {
            final int candidateLeft = repositionTaskBounds.left + (int) delta.x;
            repositionTaskBounds.left = (candidateLeft > stableBounds.left)
                    ? candidateLeft : oldLeft;
        }
        if ((ctrlType & CTRL_TYPE_RIGHT) != 0) {
            final int candidateRight = repositionTaskBounds.right + (int) delta.x;
            repositionTaskBounds.right = (candidateRight < stableBounds.right)
                    ? candidateRight : oldRight;
        }
        if ((ctrlType & CTRL_TYPE_TOP) != 0) {
            final int candidateTop = repositionTaskBounds.top + (int) delta.y;
            repositionTaskBounds.top = (candidateTop > stableBounds.top)
                    ? candidateTop : oldTop;
        }
        if ((ctrlType & CTRL_TYPE_BOTTOM) != 0) {
            final int candidateBottom = repositionTaskBounds.bottom + (int) delta.y;
            repositionTaskBounds.bottom = (candidateBottom < stableBounds.bottom)
                    ? candidateBottom : oldBottom;
        }
        // If width or height are negative or less than the minimum width or height, revert the
        // respective bounds to use previous bound dimensions.
        if (repositionTaskBounds.width() < getMinWidth(displayController, windowDecoration)) {
            repositionTaskBounds.right = oldRight;
            repositionTaskBounds.left = oldLeft;
        }
        if (repositionTaskBounds.height() < getMinHeight(displayController, windowDecoration)) {
            repositionTaskBounds.top = oldTop;
            repositionTaskBounds.bottom = oldBottom;
        }
        // If there are no changes to the bounds after checking new bounds against minimum width
        // and height, do not set bounds and return false
        if (oldLeft == repositionTaskBounds.left && oldTop == repositionTaskBounds.top
                && oldRight == repositionTaskBounds.right
                && oldBottom == repositionTaskBounds.bottom) {
            return false;
        }
        return true;
    }

    /**
     * Set bounds using a {@link SurfaceControl.Transaction}.
     */
    static void setPositionOnDrag(WindowDecoration decoration, Rect repositionTaskBounds,
            Rect taskBoundsAtDragStart, PointF repositionStartPoint, SurfaceControl.Transaction t,
            float x, float y) {
        updateTaskBounds(repositionTaskBounds, taskBoundsAtDragStart, repositionStartPoint, x, y);
        t.setPosition(decoration.mTaskSurface, repositionTaskBounds.left, repositionTaskBounds.top);
    }

    private static void updateTaskBounds(Rect repositionTaskBounds, Rect taskBoundsAtDragStart,
            PointF repositionStartPoint, float x, float y) {
        final float deltaX = x - repositionStartPoint.x;
        final float deltaY = y - repositionStartPoint.y;
        repositionTaskBounds.set(taskBoundsAtDragStart);
        repositionTaskBounds.offset((int) deltaX, (int) deltaY);
    }

    /**
     * Calculates the new position of the top edge of the task and returns true if it is below the
     * disallowed area.
     *
     * @param disallowedAreaForEndBoundsHeight the height of the area that where the task positioner
     *                                         should not finalize the bounds using WCT#setBounds
     * @param taskBoundsAtDragStart the bounds of the task on the first drag input event
     * @param repositionStartPoint initial input coordinate
     * @param y the y position of the motion event
     * @return true if the top of the task is below the disallowed area
     */
    static boolean isBelowDisallowedArea(int disallowedAreaForEndBoundsHeight,
            Rect taskBoundsAtDragStart, PointF repositionStartPoint, float y) {
        final float deltaY = y - repositionStartPoint.y;
        final float topPosition = taskBoundsAtDragStart.top + deltaY;
        return topPosition > disallowedAreaForEndBoundsHeight;
    }

    /**
     * Updates repositionTaskBounds to the final bounds of the task after the drag is finished. If
     * the bounds are outside of the valid drag area, the task is shifted back onto the edge of the
     * valid drag area.
     */
    static void onDragEnd(Rect repositionTaskBounds, Rect taskBoundsAtDragStart,
            PointF repositionStartPoint, float x, float y, Rect validDragArea) {
        updateTaskBounds(repositionTaskBounds, taskBoundsAtDragStart, repositionStartPoint,
                x, y);
        snapTaskBoundsIfNecessary(repositionTaskBounds, validDragArea);
    }

    private static void snapTaskBoundsIfNecessary(Rect repositionTaskBounds, Rect validDragArea) {
        // If we were never supplied a valid drag area, do not restrict movement.
        // Otherwise, we restrict deltas to keep task position inside the Rect.
        if (validDragArea.width() == 0) return;
        if (repositionTaskBounds.left < validDragArea.left) {
            repositionTaskBounds.offset(validDragArea.left - repositionTaskBounds.left, 0);
        } else if (repositionTaskBounds.left > validDragArea.right) {
            repositionTaskBounds.offset(validDragArea.right - repositionTaskBounds.left, 0);
        }
        if (repositionTaskBounds.top < validDragArea.top) {
            repositionTaskBounds.offset(0, validDragArea.top - repositionTaskBounds.top);
        } else if (repositionTaskBounds.top > validDragArea.bottom) {
            repositionTaskBounds.offset(0, validDragArea.bottom - repositionTaskBounds.top);
        }
    }

    private static float getMinWidth(DisplayController displayController,
            WindowDecoration windowDecoration) {
        return windowDecoration.mTaskInfo.minWidth < 0 ? getDefaultMinSize(displayController,
                windowDecoration)
                : windowDecoration.mTaskInfo.minWidth;
    }

    private static float getMinHeight(DisplayController displayController,
            WindowDecoration windowDecoration) {
        return windowDecoration.mTaskInfo.minHeight < 0 ? getDefaultMinSize(displayController,
                windowDecoration)
                : windowDecoration.mTaskInfo.minHeight;
    }

    private static float getDefaultMinSize(DisplayController displayController,
            WindowDecoration windowDecoration) {
        float density =  displayController.getDisplayLayout(windowDecoration.mTaskInfo.displayId)
                .densityDpi() * DisplayMetrics.DENSITY_DEFAULT_SCALE;
        return windowDecoration.mTaskInfo.defaultMinSize * density;
    }

    interface DragStartListener {
        /**
         * Inform the implementing class that a drag resize has started
         * @param taskId id of this positioner's {@link WindowDecoration}
         */
        void onDragStart(int taskId);
    }
}
