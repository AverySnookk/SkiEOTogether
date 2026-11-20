package dev.blockside.core.guest;

/**
 * Aggregate guest model run once per game hour. Guests arrive, buy a ticket (and maybe rentals),
 * spread across open lifts, queue, ride, ski and leave after a while. Pure maths so it's testable.
 */
public final class GuestSim {
    public record Inputs(double reputation, int openRuns, int openLifts, double liftCapacityPerHour,
                         int ticketBoothCapacity, boolean rentalShop, double baseArrivals, double seasonFactor,
                         double ridesPerGuestHour, double guestsOnMountain, double stayHours) {}

    public record Outputs(int arrivals, int rentals, double guestsOnMountain, double queue, double waitMinutes,
                          double ridesTaken) {}

    public static Outputs hour(Inputs in) {
        boolean operating = in.openRuns > 0 && in.openLifts > 0 && in.ticketBoothCapacity > 0 && in.seasonFactor > 0;
        double onMountain = in.guestsOnMountain * Math.max(0, 1 - 1 / Math.max(1, in.stayHours));
        if (!operating) return new Outputs(0, 0, onMountain, 0, 0, 0);
        double repFactor = 0.25 + 1.5 * (in.reputation / 100.0);
        double terrainFactor = Math.min(2.5, 0.6 + 0.4 * in.openRuns + 0.3 * in.openLifts);
        double demand = in.baseArrivals * repFactor * terrainFactor * in.seasonFactor;
        int arrivals = (int) Math.min(in.ticketBoothCapacity, Math.round(demand));
        int rentals = in.rentalShop ? (int) Math.round(arrivals * 0.35) : 0;
        onMountain += arrivals;
        double rideDemand = onMountain * in.ridesPerGuestHour;
        double rides = Math.min(rideDemand, in.liftCapacityPerHour);
        double queue = Math.max(0, rideDemand - in.liftCapacityPerHour) * 0.5;
        double wait = in.liftCapacityPerHour <= 0 ? 60 : Math.min(60, queue / in.liftCapacityPerHour * 60 + 1);
        return new Outputs(arrivals, rentals, onMountain, queue, wait, rides);
    }
}
