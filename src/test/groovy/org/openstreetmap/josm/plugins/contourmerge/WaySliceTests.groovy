package org.openstreetmap.josm.plugins.contourmerge

import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.openstreetmap.josm.data.osm.DataSet
import org.openstreetmap.josm.data.osm.Node
import org.openstreetmap.josm.data.osm.Way
import org.openstreetmap.josm.plugins.contourmerge.fixtures.JOSMFixture

import java.util.stream.Collectors

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.not

class SliceBoundaryMatcher extends
        BaseMatcher<Optional<WaySlice.SliceBoundary>> {
    private int expectedStart
    private int expectedEnd
    SliceBoundaryMatcher(int expectedStart, int expectedEnd) {
        this.expectedStart = expectedStart
        this.expectedEnd = expectedEnd
    }
    @Override
    boolean matches(Object item) {
        Optional<WaySlice.SliceBoundary> boundary = item as
                Optional<WaySlice.SliceBoundary>
        if (!boundary.isPresent()) return false
        if (boundary.get().start != expectedStart) return false
        if (boundary.get().end != expectedEnd) return false
        return true
    }

    @Override
    void describeTo(Description description) {
        description.appendText(String.format(
            "slice boundary: start=%s, end=%s", expectedStart, expectedEnd
        ))
    }

    @Override
    void describeMismatch(Object item, Description description) {
        def sliceBoundary = item as Optional<WaySlice.SliceBoundary>
        description.appendText("matched against SliceBoundary:")
        description.appendText(
            sliceBoundary.isPresent()
                ? sliceBoundary.get().toString()
                : "empty optional")
    }
}

class WaySliceBaseWayMatcher extends
        BaseMatcher<Optional<WaySlice>> {
    private Way baseWay
    WaySliceBaseWayMatcher(Way baseWay) {
        this.baseWay = baseWay
    }

    @Override
    boolean matches(Object item) {
        def waySlice = item as Optional<WaySlice>
        if (!waySlice.isPresent()) return false
        return waySlice.get().way == baseWay
    }

    @Override
    void describeTo(Description description) {
        description.appendText(String.format(
            "way slice with base way: %s", baseWay.toString()
        ))
    }

    @Override
    void describeMismatch(Object item, Description description) {
        def waySlice = item as Optional<WaySlice>
        description.appendText("matched against WaySlice:")
        description.appendText(
            waySlice.isPresent()
                ? waySlice.get().toString()
                : "empty optional")
    }
}

class WaySliceBoundaryMatcher extends
        BaseMatcher<Optional<WaySlice>> {
    private int start
    private int end
    WaySliceBoundaryMatcher(int start, int end) {
        this.start = start
        this.end = end
    }
    @Override
    boolean matches(Object item) {
        def waySlice = item as Optional<WaySlice>
        if (!waySlice.isPresent()) return false
        return waySlice.get().start == start && waySlice.get().end == end
    }

    @Override
    void describeTo(Description description) {
        description.appendText(String.format(
            "way slice boundary: start=%s, end=%s", start, end
        ))
    }

    @Override
    void describeMismatch(Object item, Description description) {
        def waySlice = item as Optional<WaySlice>
        description.appendText("matched against WaySlice:")
        description.appendText(
            waySlice.isPresent()
                ? waySlice.get().toString()
                : "empty optional")
    }
}

class TestCaseWithJOSMFixture {
    @BeforeClass
    static void startJOSMFixtures() {
        JOSMFixture.createUnitTestFixture().init()
    }
}

@RunWith(Enclosed.class)
class WaySliceTest {

    static def isBoundary(int start, int end) {
        new SliceBoundaryMatcher(start, end)
    }

    static def isSliceForWay(Way baseWay) {
        return new WaySliceBaseWayMatcher(baseWay)
    }

    static def isSliceWithBoundary(int start, int end) {
        return new WaySliceBoundaryMatcher(start, end)
    }

    def shouldFail = new GroovyTestCase().&shouldFail

    static def newNode(id){
        return new Node(id)
    }

    static def newWay(id, Node... nodes){
        Way w = new Way(id,1)
        w.setNodes(Arrays.asList(nodes))
        return w
    }

    static def newWay(id, List<Node> nodes){
        Way w = new Way(id,1)
        w.setNodes(nodes)
        return w
    }

    static def newNodes(int from, int to) {
        return (from..to).collect {newNode(it)}.toList()
    }

    @BeforeClass
    static void startJOSMFixtures() {
        JOSMFixture.createUnitTestFixture().init()
    }

    @Test
    void constructor_inDirection(){
        def w = newWay(1, newNode(1), newNode(2), newNode(4), newNode(5))

        WaySlice ws
        ws = new WaySlice(w, 0, 1)
        assert ws.getWay() == w
        assert ws.getStart() == 0
        assert ws.getEnd() == 1

        ws = new WaySlice(w, 1, 3)
        assert ws.getWay() == w
        assert ws.getStart() == 1
        assert ws.getEnd() == 3

        shouldFail(NullPointerException) {
            ws = new WaySlice(null, 1, 3) // way must not be null
        }

        shouldFail(IllegalArgumentException){
            ws = new WaySlice(w, -1, 3)  // start index >= 0 required
        }

        shouldFail(IllegalArgumentException){
            ws = new WaySlice(w, 4, 3)   // start index < num nodes required
        }

        shouldFail(IllegalArgumentException){
            ws = new WaySlice(w, 0, -1)  // end index >= 0 required
        }

        shouldFail(IllegalArgumentException){
            ws = new WaySlice(w, 0, 4)   // end index < num nodes required
        }

        shouldFail(IllegalArgumentException){
            ws = new WaySlice(w, 3, 2)  // start < end required
        }

        shouldFail(IllegalArgumentException){
            ws = new WaySlice(w, 3, 3)  // start < end required
        }
    }

    @Test
    void constructor_inOppositeDirection(){
        def n = newNode(1)
        // this is a closed way
        def w = newWay(1, n, newNode(2), newNode(4), newNode(5), n)

        WaySlice ws
        ws = new WaySlice(w, 0, 1, true)
        assert ws.getWay() == w
        assert ws.getStart() == 0
        assert ws.getEnd() == 1
        assert ws.isInDirection()

        ws = new WaySlice(w, 0, 1, false)
        assert ws.getWay() == w
        assert ws.getStart() == 0
        assert ws.getEnd() == 1
        assert ! ws.isInDirection()

        ws = new WaySlice(w, 1, 3, false)
        assert ws.getWay() == w
        assert ws.getStart() == 1
        assert ws.getEnd() == 3
        assert ! ws.isInDirection()


        shouldFail(NullPointerException) {
            ws = new WaySlice(null, 1, 3,false) // way must not be null
        }

        shouldFail(IllegalArgumentException){
            ws = new WaySlice(w, -1, 3,false)  // start index >= 0 required
        }

        shouldFail(IllegalArgumentException){
            ws = new WaySlice(w, 4, 3, false)   // start index < num nodes required
        }

        shouldFail(IllegalArgumentException){
            ws = new WaySlice(w, 0, -1, false)  // end index >= 0 required
        }

        shouldFail(IllegalArgumentException){
            ws = new WaySlice(w, 0, 4, false)   // end index < num nodes required
        }

        shouldFail(IllegalArgumentException){
            ws = new WaySlice(w, 3, 2, false)  // start < end required
        }

        shouldFail(IllegalArgumentException){
            ws = new WaySlice(w, 3, 3, false)  // start < end required
        }

        w = newWay(2, n, newNode(2), newNode(3), newNode(4))
        shouldFail(IllegalArgumentException){
            ws = new WaySlice(w, 0,1, false)  // way slice in opposite direction not allowed
                                              // for an open way
        }
    }

    @Test
    void getStartTearOffIndex() {
        def w = newWay(1, newNodes(1,5)) // an open way

        WaySlice ws
        ws = new WaySlice(w, 2,3)
        assert ws.getStartTearOffIdx() == 1

        ws = new WaySlice(w, 0, 3)
        assert ws.getStartTearOffIdx() == -1 // no tear off node available

        def n = newNode(1)
        w = newWay(1, n, *(newNodes(2,5)), n) // a closed way

        /*
        *+------------------------------------------------+
       * |                                                |
       * n1----------n2---------n3-----------n4-----------n5
       *                        x *********** x              ~ slice
       *             ^                                       ~ expected lower tear off index
       */
        ws = new WaySlice(w, 2, 3)
        assert ws.getStartTearOffIdx() == 1

        /*
        *+------------------------------------------------+
       * |                                                |
       * n1----------n2---------n3-----------n4-----------n5
       * x *********** x                                       ~ slice
       *                                                  ^    ~ expected lower tear off index
       */
        ws = new WaySlice(w, 0, 1)
        assert ws.getStartTearOffIdx() == 4

        /*
        *+------------------------------------------------+
       * |                                                |
       * n1----------n2---------n3-----------n4-----------n5
       * x ********************************************** x    ~ slice
       *                                                       ~ expected lower tear off index
       */
        ws = new WaySlice(w, 0, 4)
        assert ws.getStartTearOffIdx() == -1


        /*
        *    +------------------------------------------------+
       *     |                                                |
       *     n1----------n2---------n3-----------n4-----------n5
       * ****x                                                x***   ~ slice
       *                                          ^                  ~ expected upper tear off index
       */
        ws = new WaySlice(w, 0, 4, false)
        assert ws.getStartTearOffIdx() == 3
    }


    @Test
    void getEndTearOffIndex() {
        def w = newWay(1, newNodes(1, 5)) // an open way

        WaySlice ws
        ws = new WaySlice(w, 1,2)
        assert ws.getEndTearOffIdx() == 3

        ws = new WaySlice(w, 0, 4)
        assert ws.getEndTearOffIdx() == -1 // no tear off node available

        def n = newNode(1)
        w = newWay(1, n, *(newNodes(2, 5)),n) // a closed way

        /*
        *+------------------------------------------------+
       * |                                                |
       * n1----------n2---------n3-----------n4-----------n5
       *                        x *********** x              ~ slice
       *             ^                                       ~ expected upper tear off index
       */
        ws = new WaySlice(w, 2, 3)
        assert ws.getEndTearOffIdx() == 4


        /*
        *+------------------------------------------------+
       * |                                                |
       * n1----------n2---------n3-----------n4-----------n5
       *                                     x *********** x  ~ slice
       * ^                                                    ~ expected upper tear off index
       */
        ws = new WaySlice(w, 3, 4)
        assert ws.getEndTearOffIdx() == 0

        /*
        *+------------------------------------------------+
       * |                                                |
       * n1----------n2---------n3-----------n4-----------n5
       * x *********** x                                       ~ slice
       *                        ^                              ~ expected upper tear off index
       */
        ws = new WaySlice(w, 0, 1)
        assert ws.getEndTearOffIdx() == 2

        /*
        *+------------------------------------------------+
       * |                                                |
       * n1----------n2---------n3-----------n4-----------n5
       * x ********************************************** x    ~ slice
       *                                                       ~ expected upper tear off index
       */
        ws = new WaySlice(w, 0, 4)
        assert ws.getEndTearOffIdx() == -1


        /*
        *    +------------------------------------------------+
       *     |                                                |
       *     n1----------n2---------n3-----------n4-----------n5
       * ****x                                                x***   ~ slice
       *                  ^                                          ~ expected upper tear off index
       */
        ws = new WaySlice(w, 0, 4, false)
        assert ws.getEndTearOffIdx() == 1
    }

    @Test
    void getNumSegments() {
        def w = newWay(1, newNodes(1, 5)) // an open way

        def ws = new WaySlice(w, 0, 3)
        assert ws.getNumSegments() == 3

        ws = new WaySlice(w, 1, 2)
        assert ws.getNumSegments() == 1

        Node n = newNode(1)
        w = newWay(1, n, *(newNodes(2, 5)), n) // a closed way

        ws = new WaySlice(w, 0, 3)
        assert ws.getNumSegments() == 3
        ws = ws.getOpositeSlice()
        assert ws.getNumSegments() == 2

        ws = new WaySlice(w, 1, 2)
        assert ws.getNumSegments() == 1
        ws = ws.getOpositeSlice()
        assert ws.getNumSegments() == 4
    }


    def  n(i){
        return new Node(i)
    }

    @Test
    void replaceNodes_OpenWay() {
        def w = newWay(1, newNodes(1, 5)) // an open way
        def newnodes = newNodes(10,12)

        def ws = new WaySlice(w, 1, 2)
        Way wn = ws.replaceNodes(newnodes)
        assert wn.getNodes() == [n(1), n(10), n(11), n(12), n(4), n(5)]

        ws = new WaySlice(w, 0, 2)
        wn = ws.replaceNodes(newnodes)
        assert wn.getNodes() == [n(10), n(11), n(12), n(4), n(5)]

        ws = new WaySlice(w, 3, 4)
        wn = ws.replaceNodes(newnodes)
        assert wn.getNodes() == [n(1),n(2), n(3), n(10), n(11), n(12)]

        ws = new WaySlice(w, 0, 4)
        wn = ws.replaceNodes(newnodes)
        assert wn.getNodes() == [n(10), n(11), n(12)]
    }

    @Test
    void replaceNodes_ClosedWay_InDirection() {
        Node n1 = new Node(1)
        def w = newWay(1, n1, *(newNodes(2,5)), n1) // a closed way
        def newnodes = newNodes(10, 12)

        def ws = new WaySlice(w, 1, 2)
        Way wn = ws.replaceNodes(newnodes)
        assert wn.getNodes() == [n1, n(10), n(11), n(12), n(4), n(5), n1]

        ws = new WaySlice(w, 0, 2)
        wn = ws.replaceNodes(newnodes)
        assert wn.getNodes() == [n(10), n(11), n(12), n(4), n(5), n(10)]

        ws = new WaySlice(w, 3, 4)
        wn = ws.replaceNodes(newnodes)
        assert wn.getNodes() == [n(1),n(2), n(3), n(10), n(11), n(12), n(1)]

        ws = new WaySlice(w, 0, 4)
        wn = ws.replaceNodes(newnodes)
        assert wn.getNodes() == [n(10), n(11), n(12), n(10)]
    }

    @Test
    void replaceNodes_ClosedWay_ReverseDirection() {
        Node n1 = new Node(1)
        def nodes = [n1] + (2..5).collect{ newNode(it)} + [n1] // a closed way
        def w = newWay(1, *nodes)
        def newnodes = (10..12).collect {newNode(it)}
        def ws = new WaySlice(w, 1, 2, false)
        Way wn = ws.replaceNodes(newnodes)
        assert wn.getNodes() == [n(10), n(11), n(12), n(10)]

        ws = new WaySlice(w, 0, 2, false)
        wn = ws.replaceNodes(newnodes)
        assert wn.getNodes() == [n(10), n(11), n(12), n(2), n(10)]

        ws = new WaySlice(w, 0, 4, false)
        wn = ws.replaceNodes(newnodes)
        assert wn.getNodes() == [n(10), n(11), n(12), n(2), n(3), n(4), n(10)]
    }



    static class sliceBoundaryTest extends TestCaseWithJOSMFixture{

        @Test(expected = IllegalArgumentException.class)
        void rejectWayNodesWithTooFewNodes() {
            def wayNodes = []
            def sliceNodes = [newNode(1), newNode(2)]
            WaySlice.findSliceBoundary(wayNodes, sliceNodes)
        }

        @Test(expected = IllegalArgumentException.class)
        void rejectSliceNodesWithTooFewNodes() {
            def wayNodes = [newNode(1), newNode(2)]
            def sliceNodes = []
            WaySlice.findSliceBoundary(wayNodes, sliceNodes)
        }

        @Test
        void acceptSliceOfMinimalLengthAtBeginning() {
            def wayNodes = (1..10).collect { newNode(it) }
            def sliceNodes = [wayNodes[0], wayNodes[1]]
            def boundary = WaySlice.findSliceBoundary(wayNodes, sliceNodes)
            assertThat(boundary.isPresent(), equalTo(true))
            assertThat(boundary, isBoundary(0, 1))
        }

        @Test
        void acceptSliceOfMinimalLengthAtEnd() {
            def wayNodes = (1..10).collect { newNode(it) }
            def sliceNodes = [wayNodes[8], wayNodes[9]]
            def boundary = WaySlice.findSliceBoundary(wayNodes, sliceNodes)
            assertThat(boundary.isPresent(), equalTo(true))
            assertThat(boundary, isBoundary(8, 9))
        }

        @Test
        void acceptSliceOfMinimalLengthInTheMiddle() {
            def wayNodes = (1..10).collect { newNode(it) }
            def sliceNodes = [wayNodes[4], wayNodes[5]]
            def boundary = WaySlice.findSliceBoundary(wayNodes, sliceNodes)
            assertThat(boundary.isPresent(), equalTo(true))
            assertThat(boundary, isBoundary(4, 5))
        }

        @Test
        void acceptCompleteWayAsSlice() {
            def wayNodes = (1..10).collect { newNode(it) }
            def sliceNodes = wayNodes
            def boundary = WaySlice.findSliceBoundary(wayNodes, sliceNodes)
            assertThat(boundary.isPresent(), equalTo(true))
            assertThat(boundary, isBoundary(0, 9))
        }

        @Test
        void rejectFullyDisjointSlice() {
            def wayNodes = (1..10).collect { newNode(it) }
            def sliceNodes = (11..14).collect { newNode(it) }
            def boundary = WaySlice.findSliceBoundary(wayNodes, sliceNodes)
            assertThat(boundary.isPresent(), equalTo(false))
        }

        @Test
        void rejectPartiallyDisjointSlice() {
            def wayNodes = (1..10).collect { newNode(it) }
            def sliceNodes = [wayNodes[1], wayNodes[2], newNode(11)]
            def boundary = WaySlice.findSliceBoundary(wayNodes, sliceNodes)
            assertThat(boundary.isPresent(), equalTo(false))
        }

        @Test
        void rejectOverlappingSliceAtBeginning() {
            def wayNodes = (1..10).collect { newNode(it) }
            def sliceNodes = [newNode(11), wayNodes[0], wayNodes[1]]
            def boundary = WaySlice.findSliceBoundary(wayNodes, sliceNodes)
            assertThat(boundary.isPresent(), equalTo(false))
        }

        @Test
        void rejectOverlappingSliceAtEnd() {
            def wayNodes = (1..10).collect { newNode(it) }
            def sliceNodes = [wayNodes[8], wayNodes[9], newNode(11)]
            def boundary = WaySlice.findSliceBoundary(wayNodes, sliceNodes)
            assertThat(boundary.isPresent(), equalTo(false))
        }
    }


    static class buildWaySlice extends TestCaseWithJOSMFixture {

        @Test(expected=NullPointerException.class)
        void rejectNullWay() {
            def way = null
            def slice = (0..1).collect {newNode(it)}
            WaySlice.buildWaySlice(way, slice)
        }

        @Test(expected=NullPointerException.class)
        void rejectNullSlice() {
            def way = newWay(1, newNode(1), newNode(2))
            def slice = null
            WaySlice.buildWaySlice(way, slice)
        }

        @Test(expected=IllegalArgumentException.class)
        void rejectIncompleteWay() {
            def way = new Way(1)
            def slice = (0..1).collect {newNode(it)}
            WaySlice.buildWaySlice(way, slice)
        }

        @Test(expected=IllegalArgumentException.class)
        void rejectSliceIfToShort() {
            def way = newWay(1, newNode(1), newNode(2))
            def slice = [newNode(3)]
            WaySlice.buildWaySlice(way, slice)
        }

        static class fromOpenWay extends TestCaseWithJOSMFixture {

            @Test
            void acceptSliceOfMinimalLengthAtBeginning() {
                def wayNodes = newNodes(1, 10)
                def sliceNodes = [wayNodes[0], wayNodes[1]]
                def way = newWay(1, *wayNodes)
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes)
                assertThat(waySlice, isSliceForWay(way))
                assertThat(waySlice, isSliceWithBoundary(0, 1))
            }

            @Test
            void acceptSliceOfMinimalLengthAtBeginningOfReversedWay() {
                def wayNodes = newNodes(1, 10)
                def sliceNodes = [wayNodes[0], wayNodes[1]]
                def way = newWay(1, *wayNodes.reverse())
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes.reverse())
                assertThat(waySlice, isSliceForWay(way))
                assertThat(waySlice, isSliceWithBoundary(8, 9))
            }

            @Test
            void acceptSliceOfMinimalLengthAtEnd() {
                def wayNodes = newNodes(1, 10)
                def sliceNodes = [wayNodes[8], wayNodes[9]]
                def way = newWay(1, *wayNodes)
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes)
                assertThat(waySlice, isSliceForWay(way))
                assertThat(waySlice, isSliceWithBoundary(8, 9))
            }

            @Test
            void acceptSliceOfMinimalLengthAtEndOfReversedWay() {
                def wayNodes = newNodes(1, 10)
                def sliceNodes = [wayNodes[8], wayNodes[9]]
                def way = newWay(1, *wayNodes.reverse())
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes.reverse())
                assertThat(waySlice, isSliceForWay(way))
                assertThat(waySlice, isSliceWithBoundary(0, 1))
            }

            @Test
            void acceptSliceOfMinimalLengthInTheMiddle() {
                def wayNodes = newNodes(1, 10)
                def sliceNodes = [wayNodes[4], wayNodes[5]]
                def way = newWay(1, *wayNodes)
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes)
                assertThat(waySlice, isSliceForWay(way))
                assertThat(waySlice, isSliceWithBoundary(4, 5))
            }

            @Test
            void acceptSliceOfMinimalLengthInTheMiddleOfReversedWay() {
                def wayNodes = newNodes(1, 10)
                def sliceNodes = [wayNodes[2], wayNodes[3]]
                def way = newWay(1, *wayNodes.reverse())
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes.reverse())
                assertThat(waySlice, isSliceForWay(way))
                assertThat(waySlice, isSliceWithBoundary(9-3, 9-2))
            }

            @Test
            void acceptCompleteWayAsSlice() {
                def wayNodes = newNodes(1, 10)
                def sliceNodes = wayNodes
                def way = newWay(1, *wayNodes)
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes)
                assertThat(waySlice, isSliceForWay(way))
                assertThat(waySlice, isSliceWithBoundary(0, 9))
            }

            @Test
            void acceptCompleteWayAsSliceOfReversedWay() {
                def wayNodes = newNodes(1, 10)
                def sliceNodes = wayNodes
                def way = newWay(1, *wayNodes.reverse())
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes.reverse())
                assertThat(waySlice, isSliceForWay(way))
                assertThat(waySlice, isSliceWithBoundary(0, 9))
            }

            @Test
            void rejectFullyDisjointSlice() {
                def wayNodes = newNodes(1, 10)
                def sliceNodes = (11..14).collect { newNode(it) }
                def way = newWay(1, *wayNodes)
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes)
                assertThat(waySlice.isPresent(), equalTo(false))
            }

            @Test
            void rejectFullyDisjointSliceOfReversedWay() {
                def wayNodes = newNodes(1, 10)
                def sliceNodes = (11..14).collect { newNode(it) }
                def way = newWay(1, *wayNodes.reverse())
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes.reverse())
                assertThat(waySlice.isPresent(), equalTo(false))
            }

            @Test
            void rejectPartiallyDisjointSlice() {
                def wayNodes = newNodes(1, 10)
                def sliceNodes = [wayNodes[1], wayNodes[2], newNode(11)]
                def way = newWay(1, *wayNodes)
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes)
                assertThat(waySlice.isPresent(), equalTo(false))
            }

            @Test
            void rejectPartiallyDisjointSliceOfReversedWay() {
                def wayNodes = newNodes(1, 10)
                def sliceNodes = [wayNodes[1], wayNodes[2], newNode(11)]
                def way = newWay(1, *wayNodes.reverse())
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes.reverse())
                assertThat(waySlice.isPresent(), equalTo(false))
            }

            @Test
            void rejectOverlappingSliceAtBeginning() {
                def wayNodes = newNodes(1, 10)
                def sliceNodes = [newNode(11), wayNodes[0], wayNodes[1]]
                def way = newWay(1, *wayNodes)
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes)
                assertThat(waySlice.isPresent(), equalTo(false))
            }

            @Test
            void rejectOverlappingSliceAtBeginningOfReversedWay() {
                def wayNodes = newNodes(1, 10)
                def sliceNodes = [newNode(11), wayNodes[0], wayNodes[1]]
                def way = newWay(1, *wayNodes.reverse())
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes.reverse())
                assertThat(waySlice.isPresent(), equalTo(false))
            }

            @Test
            void rejectOverlappingSliceAtEnd() {
                def wayNodes = newNodes(1, 10)
                def sliceNodes = [wayNodes[8], wayNodes[9], newNode(11)]
                def way = newWay(1, *wayNodes)
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes)
                assertThat(waySlice.isPresent(), equalTo(false))
            }

            @Test
            void rejectOverlappingSliceAtEndOfReversedWay() {
                def wayNodes = newNodes(1, 10)
                def sliceNodes = [wayNodes[8], wayNodes[9], newNode(11)]
                def way = newWay(1, *wayNodes.reverse())
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes.reverse())
                assertThat(waySlice.isPresent(), equalTo(false))
            }
        }

        static class fromClosedWay extends TestCaseWithJOSMFixture {

            @Test
            void acceptSliceOfMinimalLengthAtBeginning() {
                def wayNodes = newNodes(1, 10)
                wayNodes += wayNodes[0] // close the way
                def sliceNodes = [wayNodes[0], wayNodes[1]]
                def way = newWay(1, *wayNodes)
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes)
                assertThat(waySlice, isSliceForWay(way))
                assertThat(waySlice, isSliceWithBoundary(0, 1))
            }

            @Test
            void acceptSliceOfMinimalLengthAtBeginningOfReversedWay() {
                def wayNodes = newNodes(1, 10)
                wayNodes += wayNodes[0] // close the way
                def sliceNodes = [wayNodes[0], wayNodes[1]]
                def way = newWay(1, *wayNodes.reverse())
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes.reverse())
                assertThat(waySlice, isSliceForWay(way))
                assertThat(waySlice, isSliceWithBoundary(9, 10))
            }

            @Test
            void acceptSliceOfMinimalLengthAtEnd() {
                def wayNodes = newNodes(1, 10)
                wayNodes += wayNodes[0] // close the way
                def sliceNodes = [wayNodes[9], wayNodes[10]]
                def way = newWay(1, *wayNodes)
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes)
                assertThat(waySlice, isSliceForWay(way))
                assertThat(waySlice, isSliceWithBoundary(9, 10))
            }

            @Test
            void acceptSliceOfMinimalLengthAtEndOfReversedWay() {
                def wayNodes = newNodes(1, 10)
                wayNodes += wayNodes[0] // close the way
                def sliceNodes = [wayNodes[9], wayNodes[10]]
                def way = newWay(1, *wayNodes.reverse())
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes.reverse())
                assertThat(waySlice, isSliceForWay(way))
                assertThat(waySlice, isSliceWithBoundary(0, 1))
            }

            @Test
            void acceptSliceOfMinimalLengthInTheMiddle() {
                def wayNodes = newNodes(1, 10)
                wayNodes += wayNodes[0] // close the way
                def sliceNodes = [wayNodes[4], wayNodes[5]]
                def way = newWay(1, *wayNodes)
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes)
                assertThat(waySlice, isSliceForWay(way))
                assertThat(waySlice, isSliceWithBoundary(4, 5))
            }

            @Test
            void acceptSliceOfMinimalLengthInTheMiddleOfReversedWay() {
                def wayNodes = newNodes(1, 10)
                wayNodes += wayNodes[0] // close the way
                def sliceNodes = [wayNodes[2], wayNodes[3]]
                def way = newWay(1, *wayNodes.reverse())
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes.reverse())
                assertThat(waySlice, isSliceForWay(way))
                assertThat(waySlice, isSliceWithBoundary(10-3, 10-2))
            }

            @Test
            void rejectFullyDisjointSlice() {
                def wayNodes = newNodes(1, 10)
                wayNodes += wayNodes[0] // close the way
                def sliceNodes = (11..14).collect { newNode(it) }
                def way = newWay(1, *wayNodes)
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes)
                assertThat(waySlice.isPresent(), equalTo(false))
            }


            @Test
            void rejectFullyDisjointSliceOfReversedWay() {
                def wayNodes = newNodes(1, 10)
                wayNodes += wayNodes[0] // close the way
                def sliceNodes = (11..14).collect { newNode(it) }
                def way = newWay(1, *wayNodes.reverse())
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes.reverse())
                assertThat(waySlice.isPresent(), equalTo(false))
            }

            @Test
            void rejectPartiallyDisjointSlice() {
                def wayNodes = newNodes(1, 10)
                wayNodes += wayNodes[0] // close the way
                def sliceNodes = [wayNodes[1], wayNodes[2], newNode(11)]
                def way = newWay(1, *wayNodes)
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes)
                assertThat(waySlice.isPresent(), equalTo(false))
            }

            @Test
            void rejectPartiallyDisjointSliceOfReversedWay() {
                def wayNodes = newNodes(1, 10)
                wayNodes += wayNodes[0] // close the way
                def sliceNodes = [wayNodes[1], wayNodes[2], newNode(11)]
                def way = newWay(1, *wayNodes.reverse())
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes.reverse())
                assertThat(waySlice.isPresent(), equalTo(false))
            }

            @Test
            void rejectOverlappingSliceAtBeginning() {
                def wayNodes = newNodes(1, 10)
                wayNodes += wayNodes[0] // close the way
                def sliceNodes = [newNode(11), wayNodes[0], wayNodes[1]]
                def way = newWay(1, *wayNodes)
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes)
                assertThat(waySlice.isPresent(), equalTo(false))
            }

            @Test
            void rejectOverlappingSliceAtBeginningOfReversedWay() {
                def wayNodes = newNodes(1, 10)
                wayNodes += wayNodes[0] // close the way
                def sliceNodes = [newNode(11), wayNodes[0], wayNodes[1]]
                def way = newWay(1, *wayNodes.reverse())
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes.reverse())
                assertThat(waySlice.isPresent(), equalTo(false))
            }

            @Test
            void rejectOverlappingSliceAtEnd() {
                def wayNodes = newNodes(1, 10)
                wayNodes += wayNodes[0] // close the way
                def sliceNodes = [wayNodes[8], wayNodes[9], newNode(11)]
                def way = newWay(1, *wayNodes)
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes)
                assertThat(waySlice.isPresent(), equalTo(false))
            }

            @Test
            void rejectOverlappingSliceAtEndOfReversedWay() {
                def wayNodes = newNodes(1, 10)
                wayNodes += wayNodes[0] // close the way
                def sliceNodes = [wayNodes[8], wayNodes[9], newNode(11)]
                def way = newWay(1, *wayNodes.reverse())
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes.reverse())
                assertThat(waySlice.isPresent(), equalTo(false))
            }

            @Test
            void acceptMinimalSliceWrappingAroundJoinNode() {
                def wayNodes = newNodes(1, 10)
                wayNodes += wayNodes[0] // close the way
                def sliceNodes = [wayNodes[9], wayNodes[10], wayNodes[1]]
                def way = newWay(1, *wayNodes)
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes)
                assertThat(waySlice.isPresent(), equalTo(true))
                assertThat(waySlice, isSliceWithBoundary(1, 9))
                assertThat(waySlice.get().inDirection, equalTo(false))
            }

            @Test
            void acceptMinimalSliceWrappingAroundJoinNodeOfReversedWay() {
                def wayNodes = newNodes(1, 10)
                wayNodes += wayNodes[0] // close the way
                def sliceNodes = [wayNodes[9], wayNodes[10], wayNodes[1]]
                def way = newWay(1, *wayNodes.reverse())
                def waySlice = WaySlice.buildWaySlice(way, sliceNodes.reverse())
                assertThat(waySlice.isPresent(), equalTo(true))
                assertThat(waySlice, isSliceWithBoundary(1, 9))
                assertThat(waySlice.get().inDirection, equalTo(false))
            }
        }
    }

    static class findOtherWaySlices extends TestCaseWithJOSMFixture {

        protected DataSet ds

        def newNode(int id){
            Node n = new Node(id)
            ds.addPrimitive(n)
            return n
        }

        def newWay(int id, Node... nodes){
            return newWay(id,Arrays.asList(nodes))
        }

        def newWay(int id, List<Node> nodes){
            Way w = new Way(id,1)
            w.setNodes(nodes)
            ds.addPrimitive(w)
            return w
        }

        @Before
        void setUp() {
            ds = new DataSet()
        }

        @Test
        void shouldFindThisAsWaySlice() {
            def nodes = (1..10).collect {newNode(it)}
            def way = newWay(1, *nodes)
            def waySlice = new WaySlice(way, 2, 3)
            def otherWaySlices = waySlice.findAllEquivalentWaySlices()
                .collect(Collectors.toList())
            assertThat(otherWaySlices.size(), equalTo(1))
            assertThat(otherWaySlices[0].way, equalTo(way))
            assertThat(otherWaySlices[0].start, equalTo(2))
            assertThat(otherWaySlices[0].end, equalTo(3))
        }

        @Test
        void shouldFindOtherOpenWaySliceInSameDirection() {
            def nodes = (1..10).collect {newNode(it)}
            def repWay = newWay(1, *nodes)
            def representative = new WaySlice(repWay, 2, 3)

            def otherNodes = (11..13).collect {newNode(it)} +
                // these are the shared nodes and the shared slice with
                // the representative ...
                [nodes[2], nodes[3]] +
                // ... followed by some other nodes
                (14..16).collect {newNode(it)}
            def otherWay = newWay(2, *otherNodes)

            def waySlices = representative.findAllEquivalentWaySlices()
                    .collect(Collectors.toList())

            assertThat(waySlices.size(), equalTo(2))
            def otherWaySlice = waySlices.find {it.way == otherWay}
            assertThat(otherWaySlice, not(equalTo(null)))
            assertThat(Optional.of(otherWaySlice), isSliceWithBoundary(3,4))

            def repWaySlice = waySlices.find {it.way == repWay}
            assertThat(repWaySlice, not(equalTo(null)))
            assertThat(Optional.of(repWaySlice), isSliceWithBoundary(2,3))
        }

        @Test
        void shouldFindOtherOpenWaySliceInReversedOrder() {
            def nodes = (1..10).collect {newNode(it)}
            def repWay = newWay(1, *nodes)
            def representative = new WaySlice(repWay, 2, 3)

            def otherNodes = (11..13).collect {newNode(it)} +
                // these are the shared nodes and the shared slice with
                // the representative ...
                [nodes[2], nodes[3]] +
                // ... followed by some other nodes
                (14..18).collect {newNode(it)}.reverse()
            def otherWay = newWay(2, *otherNodes)

            def waySlices = representative.findAllEquivalentWaySlices()
                    .collect(Collectors.toList())

            assertThat(waySlices.size(), equalTo(2))
            def otherWaySlice = waySlices.find {it.way == otherWay}
            assertThat(otherWaySlice, not(equalTo(null)))
            assertThat(Optional.of(otherWaySlice), isSliceWithBoundary(3,4))

            def repWaySlice = waySlices.find {it.way == repWay}
            assertThat(repWaySlice, not(equalTo(null)))
            assertThat(Optional.of(repWaySlice), isSliceWithBoundary(2,3))
        }


        @Test
        void forClosedWayShouldFindOtherOpenWaySliceInSameDirection() {
            def nodes = (1..10).collect {newNode(it)}
            nodes += nodes[0] // close the way

            def repWay = newWay(1, *nodes)
            def representative = new WaySlice(repWay, 2, 3)

            def otherNodes = (11..13).collect {newNode(it)} +
                    // these are the shared nodes and the shared slice with
                    // the representative ...
                    [nodes[2], nodes[3]] +
                    // ... followed by some other nodes
                    (14..18).collect {newNode(it)}.reverse()
            def otherWay = newWay(2, *otherNodes)

            def waySlices = representative.findAllEquivalentWaySlices()
                    .collect(Collectors.toList())

            assertThat(waySlices.size(), equalTo(2))
            def otherWaySlice = waySlices.find {it.way == otherWay}
            assertThat(otherWaySlice, not(equalTo(null)))
            assertThat(Optional.of(otherWaySlice), isSliceWithBoundary(3,4))

            def repWaySlice = waySlices.find {it.way == repWay}
            assertThat(repWaySlice, not(equalTo(null)))
            assertThat(Optional.of(repWaySlice), isSliceWithBoundary(2,3))
        }

        @Test
        void forClosedWayShouldFindOtherClosedWaySliceInReversedOrder() {
            def nodes = (1..10).collect {newNode(it)}
            nodes += nodes[0] // close the way
            def repWay = newWay(1, *nodes)
            def representative = new WaySlice(repWay, 2, 3)

            def otherNodes = (11..13).collect {newNode(it)} +
                // these are the shared nodes and the shared slice with
                // the representative ...
                [nodes[2], nodes[3]] +
                // ... followed by some other nodes
                (14..18).collect {newNode(it)}
            otherNodes += otherNodes[0] // close the other way
            otherNodes = otherNodes.reverse()  // reverse its direction
            def otherWay = newWay(2, *otherNodes)

            def waySlices = representative.findAllEquivalentWaySlices()
                    .collect(Collectors.toList())

            assertThat(waySlices.size(), equalTo(2))
            def otherWaySlice = waySlices.find {it.way == otherWay}
            assertThat(otherWaySlice, not(equalTo(null)))
            assertThat(Optional.of(otherWaySlice), isSliceWithBoundary(6,7))

            def repWaySlice = waySlices.find {it.way == repWay}
            assertThat(repWaySlice, not(equalTo(null)))
            assertThat(Optional.of(repWaySlice), isSliceWithBoundary(2,3))
        }

        @Test
        void forClosedWayShouldFindOtherOpenWaySliceWrappingAroundJoinNode() {
            def nodes = (1..10).collect {newNode(it)}
            nodes += nodes[0] // close the way
            def repWay = newWay(1, *nodes)
            def representative = new WaySlice(repWay, 1, 9,
                    false /* indirection = false */)

            def otherNodes = (11..13).collect {newNode(it)} +
                    // these are the shared nodes and the shared slice with
                    // the representative. They include the join node
                    // of the repWay as sub sequence
                    [nodes[9], nodes[0], nodes[1]] +
                    // ... followed by some other nodes
                    (14..18).collect {newNode(it)}
            def otherWay = newWay(2, *otherNodes)

            def waySlices = representative.findAllEquivalentWaySlices()
                    .collect(Collectors.toList())

            assertThat(waySlices.size(), equalTo(2))
            def otherWaySlice = waySlices.find {it.way == otherWay}
            assertThat(otherWaySlice, not(equalTo(null)))
            assertThat(Optional.of(otherWaySlice), isSliceWithBoundary(3,5))

            def repWaySlice = waySlices.find {it.way == repWay}
            assertThat(repWaySlice, not(equalTo(null)))
            assertThat(Optional.of(repWaySlice), isSliceWithBoundary(1,9))
            assertThat(repWaySlice.inDirection, equalTo(false))
        }
    }
}
