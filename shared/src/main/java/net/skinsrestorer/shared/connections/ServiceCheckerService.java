/*
 * SkinsRestorer
 * Copyright (C) 2026  SkinsRestorer Team
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.skinsrestorer.shared.connections;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.skinsrestorer.api.exception.DataRequestException;
import net.skinsrestorer.api.property.SkinProperty;
import net.skinsrestorer.shared.log.SRLogger;

import javax.inject.Inject;
import java.util.*;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ServiceCheckerService {
    private static final Map<String, UUID> PLAYER_MAP = Map.ofEntries(
            Map.entry("xknat", UUID.fromString("7dcfc130-344a-4719-9fbe-3176bc2075c6")),
            Map.entry("jeb_", UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6")),
            Map.entry("Dinnerbone", UUID.fromString("61699b2e-d327-4a01-9f1e-0ea8c3f06bc6")),
            Map.entry("Grumm", UUID.fromString("e6b5c088-0680-44df-9e1b-9bf11792291b"))
    );
    private final SRLogger logger;

    public ServiceCheckResponse checkServices() {
        ServiceCheckResponse response = new ServiceCheckResponse();

        // All external API requests (Mojang, Eclipse, Ely.by) have been removed.
        // Only MineSkin is used for skin generation via URL.
        logger.debug("Service checks for Mojang/Eclipse APIs are no longer available.");

        return response;
    }

    @Getter
    public static class ServiceCheckResponse {
        private final List<ServiceCheckMessage> results = new LinkedList<>();

        public boolean allFullySuccessful() {
            return results.stream().allMatch(ServiceCheckMessage::success);
        }

        public boolean minOneServiceUnavailable() {
            return false;
        }

        public int getSuccessCount(ServiceCheckType type) {
            return (int) results.stream().filter(message -> message.type() == type && message.success()).count();
        }

        public int getTotalCount(ServiceCheckType type) {
            return (int) results.stream().filter(message -> message.type() == type).count();
        }

        private void addResult(String message, boolean success, ServiceCheckType type) {
            results.add(new ServiceCheckMessage(message, success, type));
        }

        public enum ServiceCheckType {
            UUID,
            PROFILE
        }

        public record ServiceCheckMessage(String message, boolean success, ServiceCheckType type) {
        }
    }
}
