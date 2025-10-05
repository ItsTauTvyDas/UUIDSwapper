package me.itstautvydas.uuidswapper.processor;

import com.google.auto.service.AutoService;
import com.google.gson.annotations.SerializedName;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({
        "me.itstautvydas.uuidswapper.processor.ReadMeTitle",
        "me.itstautvydas.uuidswapper.processor.ReadMeDescription"
        // Not adding ReadMeLinkTo because it doesn't make sense if a field only has that annotation without a description
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedOptions({
        "configdoc.optionName",
        "configdoc.descriptionName",
        "configdoc.defaultName",
        "configdoc.outputFileName",
        "configdoc.optionRequiredPrefix",
        "configdoc.rawConfigurationPath",
})
@AutoService(Processor.class)
public class ReadMeProcessor extends AbstractProcessor {

    private final Map<TypeElement, ClassInfo> classes = new LinkedHashMap<>();
    private final Map<String, String> sectionsToLink = new HashMap<>();
    private Filer filer;
    private String optionName;
    private String descriptionName;
    private String defaultName;
    private String outputFileName;
    private String optionRequiredPrefix;
    private String rawConfigurationPath;

    private static List<TypeElement> getSuperClassesReverse(TypeElement type, Map<String, String> overwrittenDescriptions) {
        var list = new ArrayList<TypeElement>();
        while (true) {
            var annotation = type.getAnnotation(ReadMeCallSuperClass.class);
            if (annotation == null)
                break;
            if (overwrittenDescriptions != null)
                for (int i = 0; i < annotation.value().length; i += 2)
                    overwrittenDescriptions.put(annotation.value()[i], annotation.value()[i + 1]);
            var superClass = type.getSuperclass();
            if (superClass.getKind() != TypeKind.DECLARED)
                break;
            type = (TypeElement) ((DeclaredType) superClass).asElement();
            list.add(0, type);
        }
        return list;
    }

    private static String toReadMeSectionName(String input) {
        var result = input.stripLeading();
        result = result.toLowerCase();
        result = result.replaceAll("[\\p{Punct}]", "");
        result = result.replaceAll("\\s+", "-");
        return result;
    }

    private static String applySpaces(String string, int width) {
        if (string == null)
            return " ".repeat(width);
        var spaces = width - string.length();
        if (spaces < 0)
            return string;
        return string + " ".repeat(spaces);
    }

    private static String getJsonSerializedName(VariableElement field) {
        var serializedName = field.getAnnotation(SerializedName.class);
        if (serializedName != null && serializedName.value() != null && !serializedName.value().isBlank())
            return serializedName.value();
        return null;
    }

    private static String toKebabCase(String name) {
        var withSpaces = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ');
        var parts = withSpaces.trim().split("\\s+");
        return Arrays.stream(parts).map(String::toLowerCase).collect(Collectors.joining("-"));
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.filer = processingEnv.getFiler();
        this.optionName = processingEnv.getOptions().getOrDefault("configdoc.optionName", "Option");
        this.descriptionName = processingEnv.getOptions().getOrDefault("configdoc.descriptionName", "Description");
        this.defaultName = processingEnv.getOptions().getOrDefault("configdoc.defaultName", "If undefined");
        this.outputFileName = processingEnv.getOptions().getOrDefault("configdoc.outputFileName", "ConfigurationDocs.generated.md");
        this.optionRequiredPrefix = processingEnv.getOptions().getOrDefault("configdoc.optionRequiredPrefix", "\\*");
        this.rawConfigurationPath = processingEnv.getOptions().getOrDefault("configdoc.rawConfigurationPath", null);
    }

    private ClassInfo processClass(Element element, String prefix, ClassInfo oldClassInfo, Map<String, String> overwrittenDescriptions) {
        if (element.getKind() != ElementKind.CLASS && element.getKind() != ElementKind.RECORD)
            return null;

        var type = (TypeElement) element;
        var tableSettings = type.getAnnotation(ReadMeTableSettings.class);
        var disableDescriptions = tableSettings != null && tableSettings.disableDescription();

        ClassInfo classInfo;
        if (oldClassInfo == null) {
            var title = type.getAnnotation(ReadMeTitle.class);
            var description = type.getAnnotation(ReadMeDescription.class);
            if (title == null)
                return null;
            classInfo = classes.computeIfAbsent(type, t -> new ClassInfo(
                    title.value().isBlank() ? t.getSimpleName().toString().replaceAll("([a-z])([A-Z])", "$1 $2") : title.value(),
                    applyPlaceholders(description != null ? description.value() : ""),
                    title.order(),
                    prefix,
                    new ArrayList<>(),
                    tableSettings
            ));

            var map = new HashMap<String, String>();
            for (var superElement : getSuperClassesReverse(type, map))
                processClass(superElement, prefix, classInfo, map);
        } else {
            classInfo = oldClassInfo;
        }

        for (var enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                var field = (VariableElement) enclosed;

                var fieldDescription = field.getAnnotation(ReadMeDescription.class);
                String description = null;
                if (overwrittenDescriptions != null)
                    description = overwrittenDescriptions.get(field.getSimpleName().toString());
                var merge = field.getAnnotation(ReadMeMergeClass.class);
                if (!disableDescriptions && description == null && fieldDescription == null && merge == null)
                    continue;

                var linkTo = field.getAnnotation(ReadMeLinkTo.class);
                var defaultAnnotation = field.getAnnotation(ReadMeDefault.class);

                var name = getJsonSerializedName(field);
                if (name == null || name.isBlank())
                    name = toKebabCase(field.getSimpleName().toString());

                String fieldType = null;
                var typeMirror = field.asType();
                if (typeMirror instanceof DeclaredType)
                    fieldType = ((DeclaredType) typeMirror).asElement().getSimpleName().toString();

                List<TypeElement> sections = null;
                if (linkTo != null)
                    sections = getClasses(linkTo);

                if (disableDescriptions || fieldDescription != null || description != null) {
                    if (!disableDescriptions) {
                        if (description == null)
                            description = fieldDescription.value();
                        if (description != null) {
                            description = description
                                    .replace("\n", "<br/>")
                                    .replace("\t", "    "); // 4 spaces
                        }
                    }

                    var isRequired = field.getAnnotationMirrors().stream()
                            .map(m -> (TypeElement) m.getAnnotationType().asElement())
                            .anyMatch(te -> te.getQualifiedName()
                                    .contentEquals("me.itstautvydas.uuidswapper.annotation.RequiredProperty"));

                    classInfo.fields.add(new FieldInfo(
                            prefix + name,
                            applyPlaceholders(description),
                            sections,
                            fieldType,
                            defaultAnnotation != null ? defaultAnnotation.value() : null,
                            isRequired
                    ));
                }

                if (merge != null) {
                    var mergeElement = ((DeclaredType) field.asType()).asElement();

                    var map = new HashMap<String, String>();
                    for (var superElement : getSuperClassesReverse((TypeElement) mergeElement, map))
                        processClass(superElement, name + ".", classInfo, map);
                    processClass(mergeElement, name + ".", classInfo, null);
                }
            }
        }

        var extraFields = type.getAnnotation(ReadMeExtraFields.class);
        if (extraFields != null) {
            for (int i = 0; i < extraFields.value().length; i += 3) {
                var value = extraFields.value()[i];
                var options = new HashMap<String, String>();
                if (value.contains(";")) {
                    var parts = value.split(";");
                    for (int j = 1; j < parts.length; j++) {
                        var part = parts[j];
                        var entry = part.split("=", 2);
                        if (entry.length == 2)
                            options.put(entry[0], entry[1]);
                    }
                    value = parts[0];
                }
                classInfo.fields.add(new FieldInfo(
                        prefix + value,
                        applyPlaceholders(extraFields.value()[i + 1]),
                        null,
                        extraFields.value()[i + 2],
                        options.get("default"),
                        options.getOrDefault("required", "false").equals("true")
                ));
            }
        }

        return classInfo;
    }

    private String applyPlaceholders(String string) {
        if (string == null || string.isBlank()) return string;
        long current = System.currentTimeMillis();
        long fileTime;
        try {
            fileTime = Files.getLastModifiedTime(
                    Paths.get(filer.getResource(StandardLocation.CLASS_OUTPUT, "", outputFileName).toUri())
            ).toMillis();
        } catch (Exception ex) {
            fileTime = current;
        }

        for (int i = 1; true; i++) {
            var placeholder = i > 1 ? "{file_time_based_uuid" + i + "}" : "{file_time_based_uuid}";
            if (!string.contains(placeholder))
                break;
            string = string.replace(placeholder, UUID.nameUUIDFromBytes(
                    ByteBuffer.allocate(Long.BYTES).putLong(fileTime + i).array()).toString()
            );
        }

        return string
                .replace("{file_time}", String.valueOf(fileTime))
                .replace("{current_time}", String.valueOf(current));
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        var classCandidates = new LinkedHashSet<Element>();
        classCandidates.addAll(env.getElementsAnnotatedWith(ReadMeTitle.class));
        classCandidates.addAll(env.getElementsAnnotatedWith(ReadMeDescription.class));
        classCandidates.addAll(env.getElementsAnnotatedWith(ReadMeLinkTo.class));

        for (var element : classCandidates) {
            var classInfo = processClass(element, "", null, null);
            if (classInfo == null)
                continue;
            sectionsToLink.put(element.getSimpleName().toString(), toReadMeSectionName(classInfo.title));
        }

        if (env.processingOver() && !classes.isEmpty()) {
            String markdown = renderMarkdown();
            try {
                var file = filer.createResource(StandardLocation.CLASS_OUTPUT, "", outputFileName);
                try (Writer w = file.openWriter()) {
                    w.write(markdown);
                }
            } catch (IOException ex) {
                // Do nothing
            }
        }
        return false;
    }

    private String renderMarkdown() {
        var sortedClasses = classes.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getValue().order))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        var builder = new StringBuilder();

        if (rawConfigurationPath != null) {
            Path pathToRawConfig = null;
            try {
                pathToRawConfig = Paths.get(rawConfigurationPath);
                if (Files.exists(pathToRawConfig)) {
                    var rawConfiguration = Files.readString(pathToRawConfig);
                    builder.append("# Default configuration file contents\n\n");
                    builder.append("<details>\n");
                    builder.append("<summary>Click to reveal</summary>\n\n```json\n");
                    builder.append(rawConfiguration);
                    builder.append("\n```\n");
                    builder.append("</details>\n\n");
                }
            } catch (Exception ex) {
                System.err.println("Could not read configuration file: " + ex.getMessage());
                if (pathToRawConfig != null) {
                    System.err.println("Path: " + pathToRawConfig.toAbsolutePath());
                } else {
                    System.err.println("Path with errors: " + rawConfigurationPath);
                }
            }
        }

        builder.append("**! Options with prefixed star (\\*) are required !**\n\n");

        var first = true;
        for (Map.Entry<TypeElement, ClassInfo> entry : sortedClasses.entrySet()) {
            ClassInfo classInfo = entry.getValue();

            var disableDescriptions = classInfo.settings != null && classInfo.settings.disableDescription();

            if (!first)
                builder.append("\n");
            first = false;

            builder.append("# ").append(classInfo.title).append("\n");
            if (!classInfo.description.isBlank())
                builder.append(classInfo.description).append("\n\n");

            if (!classInfo.fields.isEmpty()) {
                var optTitleNameLength = optionName.length();
                var optDescNameLength = descriptionName.length();
                var optDefaultValueLength = 0;

                // Find max option and description lengths
                for (FieldInfo fieldInfo : classInfo.fields) {
                    if (fieldInfo.defaultValue != null) {
                        if (optDefaultValueLength == 0)
                            optDefaultValueLength = defaultName.length();
                        optDefaultValueLength = Math.max(optDefaultValueLength, formatDefaultValue(fieldInfo).length());
                    }
                    if (fieldInfo.required)
                        optTitleNameLength = Math.max(optTitleNameLength, fieldInfo.option.length() + optionRequiredPrefix.length());
                    else
                        optTitleNameLength = Math.max(optTitleNameLength, fieldInfo.option.length());
                    if (!disableDescriptions)
                        optDescNameLength = Math.max(optDescNameLength, fieldInfo.description.length());
                    if (fieldInfo.linkToSections != null) {
                        if (fieldInfo.linkToSections.size() == 1) {
                            var sectionName = getSectionToLinkTo(fieldInfo, 0);
                            if (sectionName != null)
                                optTitleNameLength += sectionName.length() + 5; // [](#) symbols count
                        } else { // Multiple sections
                            optDescNameLength += 3; // a space and ()
                            var it = fieldInfo.linkToSections.iterator();
                            for (int i = 0; it.hasNext(); i++) {
                                var section = it.next();
                                var sectionName = getSectionToLinkTo(fieldInfo, i);
                                if (sectionName == null)
                                    continue;
                                optDescNameLength += sectionName.length() + section.getSimpleName().length() + 5; // [](#) symbols count
                                if (it.hasNext())
                                    optDescNameLength += 2; // comma and a space
                            }
                        }
                    }
                }

                var fieldOptionName = applySpaces(optionName, optTitleNameLength);
                var fieldDescName = applySpaces(descriptionName, optDescNameLength);
                var fieldDefName = applySpaces(defaultName, optDefaultValueLength);

                builder.append("| ").append(fieldOptionName).append(" |");
                if (!disableDescriptions)
                    builder.append(' ').append(fieldDescName).append(" |");
                if (optDefaultValueLength != 0)
                    builder.append(' ').append(fieldDefName).append(" |");
                builder.append('\n');
                builder.append("|-").append("-".repeat(optTitleNameLength)).append("-|");
                if (!disableDescriptions)
                    builder.append('-').append("-".repeat(optDescNameLength)).append("-|");
                if (optDefaultValueLength != 0)
                    builder.append('-').append("-".repeat(optDefaultValueLength)).append("-|");
                builder.append('\n');
                for (FieldInfo fieldInfo : classInfo.fields) {
                    var option = classInfo.prefix + fieldInfo.option;
                    if (fieldInfo.required)
                        option = optionRequiredPrefix + option;
                    builder.append("| ");
                    if (fieldInfo.linkToSections != null && fieldInfo.linkToSections.size() == 1) { // Only one section
                        var sectionName = getSectionToLinkTo(fieldInfo, 0);
                        if (sectionName != null)
                            builder.append(applySpaces("[" + option + "](#" + sectionName + ")", optTitleNameLength));
                    } else {
                        builder.append(applySpaces(option, optTitleNameLength));
                    }
                    if (!disableDescriptions) {
                        var description = new StringBuilder(fieldInfo.description.replace("|", "\\|"));
                        builder.append(" | ");
                        if (fieldInfo.linkToSections != null && fieldInfo.linkToSections.size() > 1) { // Multiple sections
                            description.append(" (");
                            var it = fieldInfo.linkToSections.iterator();
                            for (int i = 0; it.hasNext(); i++) {
                                var section = it.next();
                                var sectionName = getSectionToLinkTo(fieldInfo, i);
                                if (sectionName == null)
                                    continue;
                                description.append("[").append(section.getSimpleName().toString()).append("](#").append(sectionName).append(")");
                                if (it.hasNext())
                                    description.append(", ");
                            }
                            description.append(')');
                        }
                        builder.append(applySpaces(description.toString(), optDescNameLength));
                    }
                    if (optDefaultValueLength != 0) {
                        builder.append(" | ");
                        builder.append(applySpaces(formatDefaultValue(fieldInfo), optDefaultValueLength));
                    }
                    builder.append(" |\n");
                }
            }
        }
        builder.append("\n**This file was auto-generated by an annotation processor.**");
        return builder.toString();
    }

    private String formatDefaultValue(FieldInfo fieldInfo) {
        return (fieldInfo.defaultValue == null || fieldInfo.defaultValue.isEmpty()) && fieldInfo.required ? "*required*" : fieldInfo.defaultValue;
    }

    private List<TypeElement> getClasses(ReadMeLinkTo linkTo) {
        try {
            linkTo.value(); // Trigger exception
            return null;
        } catch (MirroredTypesException mte) {
            java.util.List<TypeElement> result = new java.util.ArrayList<>(mte.getTypeMirrors().size());
            for (TypeMirror tm : mte.getTypeMirrors())
                result.add((TypeElement) ((DeclaredType) tm).asElement());
            return result;
        }
    }

    private String getSectionToLinkTo(FieldInfo fieldInfo, int index) {
        var sections = fieldInfo.linkToSections;
        if (sections == null) {
            if (fieldInfo.className == null)
                return null;
            return sectionsToLink.get(fieldInfo.className);
        }
        if (index >= 0 && index < sections.size())
            return sectionsToLink.get(sections.get(index).getSimpleName().toString());
        return null;
    }

    private record ClassInfo(String title, String description, int order, String prefix, List<FieldInfo> fields,
                             ReadMeTableSettings settings) {
    }

    private record FieldInfo(String option, String description, List<TypeElement> linkToSections, String className,
                             String defaultValue, boolean required) {
    }
}