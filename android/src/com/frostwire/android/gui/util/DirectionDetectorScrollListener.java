/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.util;

import android.widget.AbsListView;

import com.frostwire.android.gui.services.Engine;
import com.frostwire.util.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created on 11/23/16.
 *
 * @author gubatron
 * @author aldenml
 */

public final class DirectionDetectorScrollListener {
    @SuppressWarnings("unused")
    private static final Logger LOG = Logger.getLogger(DirectionDetectorScrollListener.class);

    private static final class ScrollDirectionVotes {
        private byte ups;
        private byte downs;
        ScrollDirectionVotes() { reset(); }
        void reset() { ups = downs = 0; }
        void up() {          ups++;
        }
        void down() {
            downs++;
        }
        byte total() {
            return (byte) (ups + downs);
        }
        byte delta() {
            return (byte) Math.abs(ups - downs);
        }
        byte ups() {
            return ups;
        }
        byte downs() {
            return downs;
        }
        public String toString() {
            return "ScrollDirectionVotes: total=" + total() + " ups=" + ups + " downs=" + downs + " delta=" + delta();
        }
    }

    public static AbsListView.OnScrollListener createOnScrollListener(final ScrollDirectionListener scrollDirectionListener) {
        return new AbsListView.OnScrollListener() {
            private final ScrollDirectionVotes votes = new ScrollDirectionVotes();
            private final int MIN_VOTES = 4;
            private final long DISABLE_INTERVAL_ON_EVENT = 100L;
            private int lastFirstVisibleItem;
            private AtomicBoolean enabled = new AtomicBoolean(true);
            private AtomicBoolean inMotion = new AtomicBoolean(false);
            private AtomicBoolean enabledScrollDown = new AtomicBoolean(true);
            private AtomicBoolean enabledScrollUp = new AtomicBoolean(true);

            @Override
            public void onScrollStateChanged(AbsListView absListView, int scrollState) {
                switch (scrollState) {
                    case SCROLL_STATE_IDLE:
                        inMotion.set(false);
                        // wait a little longer to call victory
                        Runnable r = new Runnable() {
                            public void run() {
                                try {
                                    Thread.sleep(400);
                                } catch (Throwable ignored) {}
                                if (!inMotion.get()) {
                                    onIdle();
                                }
                            }
                        };
                        Engine.instance().getThreadPool().submit(r);
                        break;
                    case SCROLL_STATE_FLING:
                        inMotion.set(true);
                        onFling();
                        break;
                    case SCROLL_STATE_TOUCH_SCROLL:
                        inMotion.set(true);
                        onTouchScroll();
                        break;
                }
            }

            private void onFling() {
                checkCandidates();
            }

            private void onTouchScroll() {
                checkCandidates();
            }

            private void onIdle() {
                votes.reset();
            }

            @Override
            public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!enabled.get() || scrollDirectionListener == null) {
                    return;
                }
                boolean scrollingDown = firstVisibleItem > lastFirstVisibleItem;
                boolean scrollingUp = firstVisibleItem < lastFirstVisibleItem;
                lastFirstVisibleItem = firstVisibleItem;
                if (enabledScrollDown.get() && scrollingDown) {
                    votes.down();
                } else if (enabledScrollUp.get() && scrollingUp) {
                    votes.up();
                }
                checkCandidates();
            }

            /** sets the given flag to false temporarily for interval milliseconds */
            private void disable(final long interval, final AtomicBoolean flag) {
                // stop listening for 500 seconds to be able to detect rate of change.
                flag.set(false);
                Engine.instance().getThreadPool().submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(interval);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        flag.set(true);
                    }
                });
            }

            private void checkCandidates() {
                if (votes.total() >= MIN_VOTES) {
                    // democratic check
                    if (votes.delta() > votes.total() * 0.5) {
                        boolean scrollingUp = votes.ups() > votes.downs();
                        votes.reset();
                        disable(DISABLE_INTERVAL_ON_EVENT,
                                scrollingUp ? enabledScrollUp : enabledScrollDown);
                        if (scrollingUp) {
                            scrollDirectionListener.onScrollUp();
                        } else {
                            scrollDirectionListener.onScrollDown();
                        }
                    }
                }
            }

        };
    }
}
