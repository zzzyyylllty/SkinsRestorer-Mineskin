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
import net.skinsrestorer.shared.connections.ServiceCheckerService;
import net.skinsrestorer.shared.plugin.SRPlatformAdapter;
import net.skinsrestorer.shared.plugin.SRPlugin;
import net.skinsrestorer.shared.subjects.messages.SkinsRestorerLocale;
import net.skinsrestorer.shared.utils.MetricsCounter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith({MockitoExtension.class, SRExtension.class})
class ServicesTest {
    @Mock
    private SRPlatformAdapter srPlatformAdapter;
    @Mock
    private SettingsManager settings;
    @Mock
    private SkinsRestorerLocale skinsRestorerLocale;

    @Test
    void services(Injector injector) {
        injector.register(SkinsRestorerLocale.class, skinsRestorerLocale);
        injector.register(SRPlatformAdapter.class, srPlatformAdapter);

        injector.register(SettingsManager.class, settings);

        new SRPlugin(injector, null);

        injector.getSingleton(MetricsCounter.class);
        ServiceCheckerService.ServiceCheckResponse serviceChecker = injector.getSingleton(ServiceCheckerService.class).checkServices();

        // Mojang/Eclipse API services have been removed.
        // All API requests now go through MineSkin only.
        assertTrue(serviceChecker.getResults().isEmpty());
    }
}
