/******************************************************************************
 ** Copyright (C) 2017 Yakup Ates <Yakup.Ates@rub.de>
 **
 ** This program is free software: you can redistribute it and/or modify
 ** it under the terms of the GNU General Public License as published by
 ** the Free Software Foundation, either version 3 of the License, or
 ** any later version.
 **
 ** This program is distributed in the hope that it will be useful,
 ** but WITHOUT ANY WARRANTY; without even the implied warranty of
 ** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 ** GNU General Public License for more details.
 **
 ** You should have received a copy of the GNU General Public License
 ** along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

import java.util.Deque;
import java.util.Random;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class SantaClausProblem {
    static final int NUM_ELF_TO_WAKE      = 3;
    static final int NUM_REINDEER_TO_WAKE = 9;
    static final int NUM_ELVES            = 10;
    static final int NUM_REINDEER         = 40;
    static final Random RAND              = new Random();

    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor      = Executors.newCachedThreadPool();
        BlockingDeque<Runnable> deque = new LinkedBlockingDeque<Runnable>();

        Runnable design  = timeForTask(1500, "entwickelt.");
        Runnable create  = timeForTask(2000, "bastelt.");
        Runnable deliver = timeForTask(3000, "liefert aus.");
        Runnable holiday = timeForTask(2000, "macht Urlaub.");

        Gruppenaktivitaet delivery = new Gruppenaktivitaet("Auslieferung.",
                                                           NUM_REINDEER_TO_WAKE,
                                                           deque,true, deliver);
        for (int i=1; i <= NUM_REINDEER_TO_WAKE; ++i) {
            executor.execute(new Mitarbeiter("Rentier #"+RAND.nextInt(NUM_REINDEER),
                                             delivery, holiday));
        }

        Gruppenaktivitaet problemSolving = new Gruppenaktivitaet("Treffen.",
                                                                 NUM_ELF_TO_WAKE,
                                                                 deque, false,
                                                                 design);
        for (int i=1; i <= NUM_ELF_TO_WAKE; ++i) {
            executor.execute(new Mitarbeiter("Elf #"+RAND.nextInt(NUM_ELVES),
                                             problemSolving, create));
        }

        executor.execute(new Santa(deque));
        Thread.sleep(5000);
        executor.shutdownNow();
        executor.awaitTermination(2, TimeUnit.SECONDS);
    }

    /**
     * Simuliere Verzögerung die benötigt wird um eine Aufgabe zu erledigen.
     */
    static Runnable timeForTask(final int time, final String name) {
        return new Runnable() {
            public void run() {
                try {
                    Thread.sleep(RAND.nextInt(time));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            @Override public String toString() {
                return name;
            }
        };
    }

    /**
     * Aktive Klasse: Santa
     * Santa schläft, trifft sich mit den Elfen oder liefert Geschenke aus.
     */
    static class Santa implements Runnable {
        private final BlockingQueue<Runnable> queue;

        Santa(BlockingQueue<Runnable> queue) {
            this.queue = queue;
        }

        public void run() {
            try {
                while (true) {
                        System.out.println("Santa schläft.");
                        queue.take().run();
                }
            } catch (InterruptedException e) {
                //System.err.println(e);
            }

            System.out.println("Santa ist fertig.");
        }
    }

    /**
     * Aktive Klasse: Mitarbeiter
     * Mitarbeiter erledigt seine Aufgabe oder tritt einer Gruppenaktivitaet bei
     * um gemeinsam ein Problem anzugehen.
     */
    static class Mitarbeiter implements Runnable {
        private final String name;
        private final Gruppenaktivitaet group;
        private final Runnable task;

        Mitarbeiter(String name, Gruppenaktivitaet group, Runnable task) {
            this.name  = name;
            this.group = group;
            this.task  = task;
        }

        public void run() {
            System.out.println(name + " ist eingestellt.");

            try {
                while (true) {
                    System.out.println(name + " wartet auf Santa.");
                    group.entry();  // trete Gruppe bei
                    System.out.println(name + " ist in " + group);
                    group.leave();  // verlasse Gruppe
                    System.out.println(name + " " + task);
                    task.run();
                }
            } catch (InterruptedException e) {
                //System.err.println(e);
            } catch (BrokenBarrierException e) {
                //System.err.println(e);
            }

            System.out.println(name + " ist gekündigt.");
        }
    }

    /**
     * Passive Klasse: Gruppenaktivitaet.
     * Es bildet sich eine Gruppe, damit eine Aufgabe mit Santa bearbeitet
     * werden kann. Die Mitglieder der Gruppe sind dann entweder Rentiere oder
     * Elfen. Sobald die benötigte Anzahl an Gruppenmitgliedern erreicht wird,
     * darf Santa geweckt und die Aufgabe abgearbeitet werden.
     */
    static class Gruppenaktivitaet {
        private final String topic;
        private final Semaphore team;
        private final CyclicBarrier groupBarrier;
        private final CyclicBarrier inBarrier;
        private final CyclicBarrier outBarrier;

        Gruppenaktivitaet (String name, final int size,
                           final Deque<Runnable> deque,
                           final boolean highPriority,
                           final Runnable action) {
            this.topic   = name;
            team         = new Semaphore(size, true);
            inBarrier    = new CyclicBarrier(size+1);
            outBarrier   = new CyclicBarrier(size+1);

            groupBarrier = new CyclicBarrier(size, new Runnable() {
                    public void run() {
                        Runnable task = new Runnable() {
                                public void run() {
                                    try {
                                        inBarrier.await();   // füge Mitarbeiter hinzu
                                        team.release(size);  // bilde neues Team
                                        System.out.println("Santa " + action);
                                        action.run();        // erledige Aufgabe
                                        outBarrier.await();  // entferne Mitarbeiter
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    } catch (BrokenBarrierException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                            };

                        if (highPriority) {
                            deque.addFirst(task);
                        } else {
                            deque.addLast(task);
                        }
                    }
                });
        }

        void entry() throws InterruptedException, BrokenBarrierException {
            team.acquire();        // trete team bei
            groupBarrier.await();  // Santa aufwecken
            inBarrier.await();     // koppeln
        }

        void leave() throws InterruptedException, BrokenBarrierException {
            outBarrier.await();  // abkoppeln
        }

        @Override public String toString() {
            return topic;
        }
    }
}
