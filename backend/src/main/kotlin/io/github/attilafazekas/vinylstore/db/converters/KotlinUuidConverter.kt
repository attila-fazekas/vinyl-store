/*
 * Copyright 2026 Attila Fazekas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.attilafazekas.vinylstore.db.converters

import org.komapper.core.spi.DataTypeConverter
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import java.util.UUID as JavaUUID

class KotlinUuidConverter : DataTypeConverter<Uuid, JavaUUID> {
    override val exteriorType: KType = typeOf<Uuid>()
    override val interiorType: KType = typeOf<JavaUUID>()

    override fun unwrap(exterior: Uuid): JavaUUID = exterior.toJavaUuid()

    override fun wrap(interior: JavaUUID): Uuid = interior.toKotlinUuid()
}
