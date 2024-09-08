package com.loiane.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.loiane.course.dto.CourseDTO;
import com.loiane.course.dto.CoursePageDTO;
import com.loiane.course.dto.CourseRequestDTO;
import com.loiane.course.dto.mapper.CourseMapper;
import com.loiane.exception.BusinessException;
import com.loiane.exception.RecordNotFoundException;

@QuarkusTest
class CourseServiceTest {

    @InjectMock
    CourseRepository courseRepository;

    @Inject
    CourseMapper courseMapper;

    @Inject
    CourseService courseService;

    @BeforeEach
    void setUp() {
        courseService = new CourseService(courseRepository, courseMapper);
    }

    @Test
    @DisplayName("Should return a list of courses with pagination")
    void testFindAllPageable() {
        List<Course> courseList = List.of(TestData.createValidCourse());
        Page<Course> coursePage = new PageImpl<>(courseList);
        when(courseRepository.findAll(any())).thenReturn(coursePage);
        
        List<CourseDTO> dtoList = new ArrayList<>(courseList.size());
        for (Course course : courseList) {
            dtoList.add(courseMapper.toDTO(course));
        }

        CoursePageDTO coursePageDTO = courseService.findAll(0, 5);
        assertEquals(dtoList, coursePageDTO.courses());
        assertThat(coursePageDTO.courses()).isNotEmpty();
        assertEquals(1, coursePageDTO.totalElements());
        assertThat(coursePageDTO.courses().get(0).lessons()).isNotEmpty();
        verify(courseRepository).findAll(any());
    }

    @Test
    @DisplayName("Should return a course by id")
    void testFindById() {
        Course course = TestData.createValidCourse();
        Optional<Course> ofResult = Optional.of(course);
        when(courseRepository.findById(anyLong())).thenReturn(ofResult);
        CourseDTO actualFindByIdResult = courseService.findById(1L);
        assertEquals(courseMapper.toDTO(ofResult.get()), actualFindByIdResult);
        verify(courseRepository).findById(anyLong());
    }

    @Test
    @DisplayName("Should throw NotFound exception when course not found")
    void testFindByIdNotFound() {
        when(courseRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThrows(RecordNotFoundException.class, () -> courseService.findById(123L));
        verify(courseRepository).findById(anyLong());
    }

    @Test
    @DisplayName("Should throw exception when id is not valid - findById")
    void testFindByIdInvalid() {
        assertThrows(ConstraintViolationException.class, () -> courseService.findById(-1L));
        assertThrows(ConstraintViolationException.class, () -> courseService.findById(null));
    }

    @Test
    @DisplayName("Should return a course by name")
    void testFindByName() {
        Course course = TestData.createValidCourse();
        when(courseRepository.findByName(anyString())).thenReturn(List.of(course));
        List<CourseDTO> listByName = courseService.findByName("Spring");
        assertThat(listByName).isNotEmpty();
        assertEquals(courseMapper.toDTO(course), listByName.get(0));
        verify(courseRepository).findByName(anyString());
    }

    @Test
    @DisplayName("Should create a course when valid")
    void testCreate() {
        CourseRequestDTO courseDTO = TestData.createValidCourseRequest();
        Course course = TestData.createValidCourse();
        when(courseRepository.save(any())).thenReturn(course);

        assertEquals(courseMapper.toDTO(course), courseService.create(courseDTO));
        verify(courseRepository).save(any());
    }

    @Test
    @DisplayName("Should throw an exception when creating an invalid course")
    void testCreateInvalid() {
        final List<CourseRequestDTO> courses = TestData.createInvalidCoursesDTO();
        for (CourseRequestDTO course : courses) {
            assertThrows(ConstraintViolationException.class, () -> courseService.create(course));
        }
        then(courseRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Should throw an exception when creating a duplicate course")
    void testCreateSameName() {
        CourseRequestDTO courseRequestDTO = TestData.createValidCourseRequest();
        when(courseRepository.findByName(any())).thenReturn(List.of(TestData.createValidCourse()));

        assertThrows(BusinessException.class, () -> courseService.create(courseRequestDTO));
        verify(courseRepository).findByName(any());
        verify(courseRepository, times(0)).save(any());
    }

    @Test
    @DisplayName("Should update a course when valid")
    void testUpdate() {
        Course course = TestData.createValidCourse();
        Optional<Course> ofResult = Optional.of(course);

        Course course1 = TestData.createValidCourse();
        when(courseRepository.save(any())).thenReturn(course1);
        when(courseRepository.findById(anyLong())).thenReturn(ofResult);

        CourseRequestDTO course2 = TestData.createValidCourseRequest();
        assertEquals(courseMapper.toDTO(course1), courseService.update(1L, course2));
        verify(courseRepository).save(any());
        verify(courseRepository).findById(anyLong());
    }

    @Test
    @DisplayName("Should throw an exception when updating an invalid course ID")
    void testUpdateNotFound() {
        Course course = TestData.createValidCourse();
        Optional<Course> ofResult = Optional.of(course);
        when(courseRepository.save(any())).thenThrow(new RecordNotFoundException(123L));
        when(courseRepository.findById(anyLong())).thenReturn(ofResult);

        CourseRequestDTO course1 = TestData.createValidCourseRequest();
        assertThrows(RecordNotFoundException.class, () -> courseService.update(123L, course1));
        verify(courseRepository).save(any());
        verify(courseRepository).findById(anyLong());
    }

    @Test
    @DisplayName("Should soft delete a course")
    void testDelete() {
        Course course = TestData.createValidCourse();
        Optional<Course> ofResult = Optional.of(course);
        doNothing().when(courseRepository).delete(any());
        when(courseRepository.findById(anyLong())).thenReturn(ofResult);
        courseService.delete(1L);
        verify(courseRepository).findById(anyLong());
        verify(courseRepository).delete(any());
    }

    @Test
    @DisplayName("Should return empty when course not found - delete")
    void testDeleteNotFound() {
        Course course = TestData.createValidCourse();
        Optional<Course> ofResult = Optional.of(course);
        doThrow(new RecordNotFoundException(1L)).when(courseRepository).delete(any());
        when(courseRepository.findById(anyLong())).thenReturn(ofResult);
        assertThrows(RecordNotFoundException.class, () -> courseService.delete(1L));
        verify(courseRepository).findById(anyLong());
        verify(courseRepository).delete(any());
    }

    @Test
    @DisplayName("Should throw exception when id is not valid - delete")
    void testDeleteInvalid() {
        assertThrows(ConstraintViolationException.class, () -> courseService.delete(-1L));
        assertThrows(ConstraintViolationException.class, () -> courseService.delete(null));
    }
}
