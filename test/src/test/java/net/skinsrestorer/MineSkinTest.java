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
package net.skinsrestorer;

import ch.jalu.configme.SettingsManager;
import ch.jalu.injector.Injector;
import net.skinsrestorer.shared.config.APIConfig;
import net.skinsrestorer.shared.config.AdvancedConfig;
import net.skinsrestorer.shared.connections.MineSkinAPIImpl;
import net.skinsrestorer.shared.connections.http.HttpClient;
import net.skinsrestorer.shared.connections.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, SRExtension.class})
class MineSkinTest {
    private static final String TEST_URL = "https://skinsrestorer.net/skinsrestorer-skin.png";
    private static final String FAKE_SKIN_VALUE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWJjMTIzIn19fQ==";

    @Mock
    private SettingsManager settings;

    @Test
    void services(Injector injector) {
        assertDoesNotThrow(() -> {
            HttpClient httpClient = mock(HttpClient.class);

            String fakeResponse = """
                    {
                        "skin": {
                            "uuid": "test-uuid",
                            "name": "test",
                            "visibility": "PUBLIC",
                            "variant": "CLASSIC",
                            "texture": {
                                "data": {
                                    "value": "%s",
                                    "signature": "test-signature"
                                }
                            }
                        },
                        "rateLimit": {
                            "next": {"absolute": 9999999999, "relative": 60000},
                            "delay": {"millis": 0, "seconds": 0},
                            "limit": {"limit": 60, "remaining": 59, "reset": 9999999999}
                        },
                        "success": true,
                        "errors": [],
                        "warnings": [],
                        "messages": [],
                        "links": {}
                    }
                    """.formatted(FAKE_SKIN_VALUE);

            when(httpClient.execute(any(), any(), any(), any(), any(), any(), anyInt()))
                    .thenReturn(new HttpResponse(200, fakeResponse, Map.of()));

            when(settings.getProperty(APIConfig.MINESKIN_API_KEY)).thenReturn("");
            when(settings.getProperty(APIConfig.MINESKIN_SECRET_SKINS)).thenReturn(false);

            injector.register(SettingsManager.class, settings);
            injector.register(HttpClient.class, httpClient);

            String randomUrl = TEST_URL + "?" + UUID.randomUUID();

            injector.getSingleton(MineSkinAPIImpl.class)
                    .genSkin(randomUrl, null);
        });
    }
}
