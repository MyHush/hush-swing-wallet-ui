// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import javax.swing.*;
import java.awt.*;

/**
 * Panel with gradient background etc. for pretty label presentations
 */
class PresentationPanel extends JPanel {
    private static final int GRADIENT_EXTENT = 17;

    private static final Color colorBorder = new Color(140, 145, 145);
    private static final Color colorLow = new Color(250, 250, 250);
    private static final Color colorHigh = new Color(225, 225, 230);
    private static final Stroke edgeStroke = new BasicStroke(1);

    PresentationPanel() {
        this.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 6));
    }

    public void paintComponent(final Graphics graphics) {
        if (getHeight() < GRADIENT_EXTENT + 1) {
            super.paintComponent(graphics);
            return;
        }
        final float percentageOfGradient = Math.min(0.49f, (float) GRADIENT_EXTENT / getHeight());

        final Graphics2D graphics2D = (Graphics2D) graphics;
        final float fractions[] = new float[]{ 0, percentageOfGradient, 1 - percentageOfGradient, 1f };
        final Color colors[] = new Color[]{ colorLow, colorHigh, colorHigh, colorLow };
        final LinearGradientPaint linearGradientPaint = new LinearGradientPaint(0, 0, 0, getHeight() - 1, fractions, colors);

        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics2D.setPaint(linearGradientPaint);
        graphics2D.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, GRADIENT_EXTENT, GRADIENT_EXTENT);
        graphics2D.setColor(colorBorder);
        graphics2D.setStroke(edgeStroke);
        graphics2D.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, GRADIENT_EXTENT, GRADIENT_EXTENT);
    }
}
