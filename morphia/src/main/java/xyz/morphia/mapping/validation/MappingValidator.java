package xyz.morphia.mapping.validation;

import xyz.morphia.ObjectFactory;
import xyz.morphia.annotations.Embedded;
import xyz.morphia.annotations.Property;
import xyz.morphia.annotations.Reference;
import xyz.morphia.annotations.Serialized;
import xyz.morphia.logging.Logger;
import xyz.morphia.logging.MorphiaLoggerFactory;
import xyz.morphia.mapping.MappedClass;
import xyz.morphia.mapping.Mapper;
import xyz.morphia.mapping.validation.ConstraintViolation.Level;
import xyz.morphia.mapping.validation.classrules.DuplicatedAttributeNames;
import xyz.morphia.mapping.validation.classrules.EmbeddedAndId;
import xyz.morphia.mapping.validation.classrules.EntityAndEmbed;
import xyz.morphia.mapping.validation.classrules.EntityCannotBeMapOrIterable;
import xyz.morphia.mapping.validation.classrules.MultipleId;
import xyz.morphia.mapping.validation.classrules.MultipleVersions;
import xyz.morphia.mapping.validation.classrules.MustHaveNoArgConstructor;
import xyz.morphia.mapping.validation.classrules.NoId;
import xyz.morphia.mapping.validation.fieldrules.ContradictingFieldAnnotation;
import xyz.morphia.mapping.validation.fieldrules.MapNotSerializable;
import xyz.morphia.mapping.validation.fieldrules.VersionMisuse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.Collections.sort;
import static java.util.Comparator.comparingInt;

/**
 * Validates the mapping information on classes.
 */
public class MappingValidator {

    private static final Logger LOG = MorphiaLoggerFactory.get(MappingValidator.class);
    private ObjectFactory creator;

    /**
     * Creates a mapping validator
     *
     * @param objectFactory the object factory to be used when creating throw away instances to use in validation
     */
    public MappingValidator(final ObjectFactory objectFactory) {
        creator = objectFactory;
    }

    /**
     * Validates a List of MappedClasses
     *
     * @param classes the MappedClasses to validate
     * @param mapper the Mapper to use for validation
     */
    public void validate(final Mapper mapper, final List<MappedClass> classes) {
        final Set<ConstraintViolation> ve = new TreeSet<>(comparingInt(o -> o.getLevel().ordinal()));

        final List<ClassConstraint> rules = getConstraints();
        for (final MappedClass c : classes) {
            for (final ClassConstraint v : rules) {
                v.check(mapper, c, ve);
            }
        }

        if (!ve.isEmpty()) {
            final ConstraintViolation worst = ve.iterator().next();
            final Level maxLevel = worst.getLevel();
            if (maxLevel.ordinal() >= Level.FATAL.ordinal()) {
                throw new ConstraintViolationException(ve);
            }

            // sort by class to make it more readable
            final List<LogLine> l = new ArrayList<>();
            for (final ConstraintViolation v : ve) {
                l.add(new LogLine(v));
            }
            sort(l);

            for (final LogLine line : l) {
                line.log(LOG);
            }
        }
    }

    private List<ClassConstraint> getConstraints() {
        final List<ClassConstraint> constraints = new ArrayList<>(32);

        // normally, i do this with scanning the classpath, but that´d bring
        // another dependency ;)

        // class-level
        constraints.add(new MustHaveNoArgConstructor());
        constraints.add(new MultipleId());
        constraints.add(new MultipleVersions());
        constraints.add(new NoId());
        constraints.add(new EmbeddedAndId());
        constraints.add(new EntityAndEmbed());
        constraints.add(new EntityCannotBeMapOrIterable());
        constraints.add(new DuplicatedAttributeNames());
        // constraints.add(new ContainsEmbeddedWithId());
        // field-level
        constraints.add(new MapNotSerializable());
        constraints.add(new VersionMisuse(creator));
        //
        constraints.add(new ContradictingFieldAnnotation(Reference.class, Serialized.class));
        constraints.add(new ContradictingFieldAnnotation(Reference.class, Property.class));
        constraints.add(new ContradictingFieldAnnotation(Reference.class, Embedded.class));
        //
        constraints.add(new ContradictingFieldAnnotation(Embedded.class, Serialized.class));
        constraints.add(new ContradictingFieldAnnotation(Embedded.class, Property.class));
        //
        constraints.add(new ContradictingFieldAnnotation(Property.class, Serialized.class));

        return constraints;
    }

    static class LogLine implements Comparable<LogLine> {
        private final ConstraintViolation v;

        LogLine(final ConstraintViolation v) {
            this.v = v;
        }

        @Override
        public int compareTo(final LogLine o) {
            return v.getPrefix().compareTo(o.v.getPrefix());
        }

        @Override
        public int hashCode() {
            return v.hashCode();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final LogLine logLine = (LogLine) o;

            return v.equals(logLine.v);

        }

        void log(final Logger logger) {
            switch (v.getLevel()) {
                case SEVERE:
                    logger.error(v.render());
                    break;
                case WARNING:
                    logger.warning(v.render());
                    break;
                case INFO:
                    logger.info(v.render());
                    break;
                case MINOR:
                    logger.debug(v.render());
                    break;
                default:
                    throw new IllegalStateException(format("Cannot log %s of Level %s", ConstraintViolation.class.getSimpleName(),
                                                           v.getLevel()));
            }
        }
    }
}
