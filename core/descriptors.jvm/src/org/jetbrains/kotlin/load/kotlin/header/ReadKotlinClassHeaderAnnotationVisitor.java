/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.load.kotlin.header;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.descriptors.SourceElement;
import org.jetbrains.kotlin.load.java.JvmBytecodeBinaryVersion;
import org.jetbrains.kotlin.load.kotlin.JvmMetadataVersion;
import org.jetbrains.kotlin.name.ClassId;
import org.jetbrains.kotlin.name.Name;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.kotlin.load.java.JvmAnnotationNames.*;
import static org.jetbrains.kotlin.load.kotlin.KotlinJvmBinaryClass.*;
import static org.jetbrains.kotlin.load.kotlin.header.KotlinClassHeader.Kind.*;

public class ReadKotlinClassHeaderAnnotationVisitor implements AnnotationVisitor {
    private JvmMetadataVersion metadataVersion = null;
    private JvmBytecodeBinaryVersion bytecodeVersion = null;
    private String extraString = null;
    private int extraInt = 0;
    private String packageName = null;
    private String[] data = null;
    private String[] strings = null;
    private String[] incompatibleData = null;
    private KotlinClassHeader.Kind headerKind = null;

    @Nullable
    public KotlinClassHeader createHeader() {
        if (headerKind == null) {
            return null;
        }

        if (!metadataVersion.isCompatible()) {
            incompatibleData = data;
        }

        if (metadataVersion == null || !metadataVersion.isCompatible()) {
            data = null;
        }
        else if (shouldHaveData() && data == null) {
            // This means that the annotation is found and its ABI version is compatible, but there's no "data" string array in it.
            // We tell the outside world that there's really no annotation at all
            return null;
        }

        return new KotlinClassHeader(
                headerKind,
                metadataVersion != null ? metadataVersion : JvmMetadataVersion.INVALID_VERSION,
                bytecodeVersion != null ? bytecodeVersion : JvmBytecodeBinaryVersion.INVALID_VERSION,
                data,
                incompatibleData,
                strings,
                extraString,
                extraInt,
                packageName
        );
    }

    private boolean shouldHaveData() {
        return headerKind == CLASS ||
               headerKind == FILE_FACADE ||
               headerKind == MULTIFILE_CLASS_PART;
    }

    @Nullable
    @Override
    public AnnotationArgumentVisitor visitAnnotation(@NotNull ClassId classId, @NotNull SourceElement source) {
        return classId.asSingleFqName().equals(METADATA_FQ_NAME) ? new KotlinMetadataArgumentVisitor() : null;
    }

    @Override
    public void visitEnd() {
    }

    private class KotlinMetadataArgumentVisitor implements AnnotationArgumentVisitor {
        @Override
        public void visit(@Nullable Name name, @Nullable Object value) {
            if (name == null) return;

            String string = name.asString();
            if (KIND_FIELD_NAME.equals(string)) {
                if (value instanceof Integer) {
                    headerKind = KotlinClassHeader.Kind.getById((Integer) value);
                }
            }
            else if (METADATA_VERSION_FIELD_NAME.equals(string)) {
                if (value instanceof int[]) {
                    metadataVersion = new JvmMetadataVersion((int[]) value);
                }
            }
            else if (BYTECODE_VERSION_FIELD_NAME.equals(string)) {
                if (value instanceof int[]) {
                    bytecodeVersion = new JvmBytecodeBinaryVersion((int[]) value);
                }
            }
            else if (METADATA_EXTRA_STRING_FIELD_NAME.equals(string)) {
                if (value instanceof String) {
                    extraString = (String) value;
                }
            }
            else if (METADATA_EXTRA_INT_FIELD_NAME.equals(string)) {
                if (value instanceof Integer) {
                    extraInt = (Integer) value;
                }
            }
            else if (METADATA_PACKAGE_NAME_FIELD_NAME.equals(string)) {
                if (value instanceof String) {
                    packageName = (String) value;
                }
            }
        }

        @Override
        @Nullable
        public AnnotationArrayArgumentVisitor visitArray(@NotNull Name name) {
            String string = name.asString();
            if (METADATA_DATA_FIELD_NAME.equals(string)) {
                return dataArrayVisitor();
            }
            else if (METADATA_STRINGS_FIELD_NAME.equals(string)) {
                return stringsArrayVisitor();
            }
            else {
                return null;
            }
        }

        @NotNull
        private AnnotationArrayArgumentVisitor dataArrayVisitor() {
            return new CollectStringArrayAnnotationVisitor() {
                @Override
                protected void visitEnd(@NotNull String[] result) {
                    data = result;
                }
            };
        }

        @NotNull
        private AnnotationArrayArgumentVisitor stringsArrayVisitor() {
            return new CollectStringArrayAnnotationVisitor() {
                @Override
                protected void visitEnd(@NotNull String[] result) {
                    strings = result;
                }
            };
        }

        @Override
        public void visitEnum(@NotNull Name name, @NotNull ClassId enumClassId, @NotNull Name enumEntryName) {
        }

        @Nullable
        @Override
        public AnnotationArgumentVisitor visitAnnotation(@NotNull Name name, @NotNull ClassId classId) {
            return null;
        }

        @Override
        public void visitEnd() {
        }
    }

    private abstract static class CollectStringArrayAnnotationVisitor implements AnnotationArrayArgumentVisitor {
        private final List<String> strings;

        public CollectStringArrayAnnotationVisitor() {
            this.strings = new ArrayList<String>();
        }

        @Override
        public void visit(@Nullable Object value) {
            if (value instanceof String) {
                strings.add((String) value);
            }
        }

        @Override
        public void visitEnum(@NotNull ClassId enumClassId, @NotNull Name enumEntryName) {
        }

        @Override
        public void visitEnd() {
            //noinspection SSBasedInspection
            visitEnd(strings.toArray(new String[strings.size()]));
        }

        protected abstract void visitEnd(@NotNull String[] data);
    }
}
