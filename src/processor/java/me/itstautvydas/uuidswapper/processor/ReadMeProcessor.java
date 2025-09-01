package me.itstautvydas.uuidswapper.processor;

import com.google.auto.service.AutoService;
import com.google.gson.annotations.SerializedName;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("unused") // stfu about that warning, me no like
@SupportedAnnotationTypes({
        "me.itstautvydas.uuidswapper.processor.ReadMeTitle",
        "me.itstautvydas.uuidswapper.processor.ReadMeDescription"
        // Not adding ReadMeLinkTo because it doesn't make sense if a field only has that annotation without a description
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedOptions({
        "configdoc.optionName",
        "configdoc.descriptionName"
})
@AutoService(Processor.class)
public class ReadMeProcessor extends AbstractProcessor {

    private Filer filer;
    private String optionName;
    private String descriptionName;

    private final Map<TypeElement, ClassInfo> classes = new LinkedHashMap<>();
    private final Map<String, String> sectionsToLink = new HashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.filer = processingEnv.getFiler();
        this.optionName = processingEnv.getOptions().getOrDefault("configdoc.optionName", "Option");
        this.descriptionName = processingEnv.getOptions().getOrDefault("configdoc.descriptionName", "Description");
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
                    title.value().isBlank() ? t.getSimpleName().toString().replaceAll("([a-z])([A-Z])", "$1 $2") :  title.value(),
                    description != null ? description.value() : "",
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
                String descriptionName = null;
                if (overwrittenDescriptions != null)
                    descriptionName = overwrittenDescriptions.get(field.getSimpleName().toString());
                var merge = field.getAnnotation(ReadMeMergeClass.class);
                if (!disableDescriptions && descriptionName == null && fieldDescription == null && merge == null)
                        continue;

                var linkTo = field.getAnnotation(ReadMeLinkTo.class);

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

                if (disableDescriptions || fieldDescription != null || descriptionName != null) {
                    if (!disableDescriptions) {
                        if (descriptionName == null)
                            descriptionName = fieldDescription.value();
                        if (descriptionName != null)
                            descriptionName = descriptionName
                                    .replace("\n", "<br/>")
                                    .replace("\t", "    "); // 4 spaces
                    }
                    classInfo.fields.add(new FieldInfo(
                            prefix + name,
                            descriptionName,
                            sections,
                            fieldType
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
                classInfo.fields.add(new FieldInfo(
                        prefix + extraFields.value()[i],
                        extraFields.value()[i + 1],
                        null,
                        extraFields.value()[i + 2]
                ));
            }
        }

        return classInfo;
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
                var file = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "ConfigurationDocs.generated.md");
                try (Writer w = file.openWriter()) {
                    w.write(markdown);
                }
            } catch (IOException ex) {
                // Do nothing
            }
        }
        return false;
    }

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
                var mpl = optionName.length();
                var mdl = descriptionName.length();

                // Find max option and description lengths
                for (FieldInfo fieldInfo : classInfo.fields) {
                    mpl = Math.max(mpl, fieldInfo.option.length());
                    if (!disableDescriptions)
                        mdl = Math.max(mdl, fieldInfo.description.length());
                    if (fieldInfo.linkToSections != null) {
                        if (fieldInfo.linkToSections.size() == 1) {
                            var sectionName = getSectionToLinkTo(fieldInfo, 0);
                            if (sectionName != null)
                                mpl += sectionName.length() + 5; // [](#) symbols count
                        } else { // Multiple sections
                            mdl += 3; // a space and ()
                            var it = fieldInfo.linkToSections.iterator();
                            for (int i = 0; it.hasNext(); i++) {
                                var section = it.next();
                                var sectionName = getSectionToLinkTo(fieldInfo, i);
                                if (sectionName == null)
                                    continue;
                                mdl += sectionName.length() + section.getSimpleName().length() + 5; // [](#) symbols count
                                if (it.hasNext())
                                    mdl += 2; // comma and a space
                            }
                        }
                    }
                }

                var fieldOptionName = applySpaces(optionName, mpl);
                var fieldDescName = applySpaces(descriptionName, mdl);

                builder.append("| ").append(fieldOptionName).append(" |");
                if (!disableDescriptions)
                    builder.append(' ').append(fieldDescName).append(" |");
                builder.append('\n');
                builder.append("|-").append("-".repeat(mpl)).append("-|");
                if (!disableDescriptions)
                    builder.append('-').append("-".repeat(mdl)).append("-|");
                builder.append('\n');
                for (FieldInfo fieldInfo : classInfo.fields) {
                    var option = classInfo.prefix + fieldInfo.option;
                    builder.append("| ");
                    if (fieldInfo.linkToSections != null && fieldInfo.linkToSections.size() == 1) { // Only one section
                        var sectionName = getSectionToLinkTo(fieldInfo, 0);
                        if (sectionName != null)
                            builder.append(applySpaces("[" + option + "](#" + sectionName + ")", mpl));
                    } else {
                        builder.append(applySpaces(option, mpl));
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
                        builder.append(applySpaces(description.toString(), mdl));
                    }
                    builder.append(" |\n");
                }
            }
        }
        builder.append("\n**This file was auto-generated by an annotation processor.**");
        return builder.toString();
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

    private static String toReadMeSectionName(String input) {
        var result = input.stripLeading();
        result = result.toLowerCase();
        result = result.replaceAll("[\\p{Punct}]", "");
        result = result.replaceAll("\\s+", "-");
        return result;
    }

    private static String applySpaces(String string, int width) {
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

    private record ClassInfo(String title, String description, int order, String prefix, List<FieldInfo> fields, ReadMeTableSettings settings) {}
    private record FieldInfo(String option, String description, List<TypeElement> linkToSections, String className) {}
}
