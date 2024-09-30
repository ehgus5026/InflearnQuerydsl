package study.querydsl.repository;

import com.querydsl.core.QueryResults;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.support.QuerydslRepositorySupport;
import org.springframework.data.support.PageableExecutionUtils;
import study.querydsl.dto.MemberSearchCondition;
import study.querydsl.dto.MemberTeamDto;
import study.querydsl.dto.QMemberTeamDto;
import study.querydsl.entity.Member;

import java.util.List;

import static io.micrometer.common.util.StringUtils.isEmpty;
import static org.springframework.util.StringUtils.hasText;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

// extends QuerydslRepositorySupport
public class MemberRepositoryImpl implements MemberRepositoryCustom {

    // QuerydslRepositorySupport를 상속 받으면 이 안에 Entitymanager를 갖고 있어서 따로 주입 안해도 됨.
//    public MemberRepositoryImpl(EntityManager em) {
//        super(Member.class);
//        this.queryFactory = new JPAQueryFactory(em);
//    }

    private final JPAQueryFactory queryFactory;

    public MemberRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    // 일단 이 방식을 권장.(where를 하나로 묶지 말고, 다른 곳에서도 쓸 수 있게)
    // 반환 타입이 Member로 바껴도 queryFactory 문만 수정 해주면 사용 가능.
    @Override
    public List<MemberTeamDto> search(MemberSearchCondition condition) {

        // 직접 엔티티 매니저를 이렇게 주입 받을 수 있음.
//        EntityManager em = getEntityManager();

        // QuerydslRepositorySupport를 활용한 방법
/*        List<MemberTeamDto> result = from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                )
                .select(new QMemberTeamDto(
                        member.id.as("memberId"),
                        member.username,
                        member.age,
                        team.id.as("teamId"),
                        team.name.as("teamName")))
                .fetch();*/

        return queryFactory
                .select(new QMemberTeamDto(member.id,
                        member.username,
                        member.age,
                        team.id,
                        team.name))
                .from(member)
                .leftJoin(member.team, team)
                .where(usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe()))
                .fetch();
    }

    @Override
    public Page<MemberTeamDto> searchPageSimple(MemberSearchCondition condition, Pageable pageable) {
        // 얘는 fetchResults()가 알아서 카운트 쿼리까지 하기 때문에 무조건 카운트 쿼리가 나감.
        QueryResults<MemberTeamDto> results = queryFactory
                .select(new QMemberTeamDto(member.id,
                        member.username,
                        member.age,
                        team.id,
                        team.name))
                .from(member)
                .leftJoin(member.team, team)
                .where(usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize()) // 한 번 조회할 때마다 몇 개 가져올 지
                .fetchResults(); // fetch()를 쓰면 List<>로 데이터 컨텐츠를 바로 가져옴. fetchResults()로 가져오면 컨텐츠용 쿼리, 카운트용 쿼리 2개 나감.

        List<MemberTeamDto> content = results.getResults();
        long total = results.getTotal();

        return new PageImpl<>(content, pageable, total); // PageImpl은 Page의 구현체
    }

    /**
     * QuerydslRepositorySupport를 활용한 페이징
     * getQuerydsl().applyPagination() : 스프링 데이터가 제공하는 페이징을 Querydsl로 편리하게 변환 가능(단 Sort는 오류발생, QSort만 가능)
     * from() 으로 시작 가능(최근에는 QueryFactory를 사용해서 select()로 시작하는 것이 더 명시적)
     * EntityManager 제공
     *
     * -한계
     * Querydsl 3.x 버전을 대상으로 만듬
     * Querydsl 4.x에 나온 JPAQueryFactory로 시작할 수 없음.
     *  -> select로 시작할 수 없음(from으로 시작해야 함.)
     *  QueryFactory 제공 하지 않음.
     *  스프링 데이터 Sort 기능이 정상 동작하지 않음.
     */
    /*public Page<MemberTeamDto> searchPageSimple2(MemberSearchCondition condition, Pageable pageable) {

        JPQLQuery<MemberTeamDto> jpaQuery = from(member) // .fetch()로 받지 않고 중간에 받으면 JPQLQuery로 받음.
                .leftJoin(member.team, team)
                .where(usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                )
                .select(new QMemberTeamDto(
                        member.id,
                        member.username,
                        member.age,
                        team.id,
                        team.name));

        JPQLQuery<MemberTeamDto> query = getQuerydsl().applyPagination(pageable, jpaQuery);// 안에 보면 offset, limit를 넣어줌.
        QueryResults<MemberTeamDto> results = query.fetchResults();

        List<MemberTeamDto> content = results.getResults();
        long total = results.getTotal();

        return new PageImpl<>(content, pageable, total); // PageImpl은 Page의 구현체
    }*/

    // 카운트 쿼리 별도로 분리하기
    @Override
    public Page<MemberTeamDto> searchPageComplex(MemberSearchCondition condition, Pageable pageable) {
        // 컨텐트만 가져오기
        List<MemberTeamDto> content = queryFactory
                .select(new QMemberTeamDto(member.id,
                        member.username,
                        member.age,
                        team.id,
                        team.name))
                .from(member)
                .leftJoin(member.team, team)
                .where(usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        // Querydsl sort는 조인이 없는 관계에서만 가능해서 orderBy()로 그냥 하는게 나음.

        // 얘는 직접 TotalCountQuery를 날리기 (fetch()로 컨텐트를 가져오고 카운트 쿼리를 분리한 것.)
        // leftJoin이 필요 없을 때 이런 방식으로 하면 카운트 쿼리 최적화 가능.
        // 혹은 카운트 쿼리 먼저 보고 없으면 컨텐트 쿼리를 안 날리려고 할 때
        long total = queryFactory
                .select(member)
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                )
                .fetchCount();

        JPAQuery<Member> countQuery = queryFactory
                .select(member)
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                );

//        return new PageImpl<>(content, pageable, total);
        // 페이지 : 100이고 컨텐츠 양이 3인데 전체 카운트 쿼리 날리면 낭비니까 이렇게 컨텐츠 양이 페이지 양보다 적을 때 토탈 카운트를 씀.(페이지 시작이면서 컨텐츠 크기가 페이지 크기보다 작을 때)
        // -> 총 데이터 개수가 100개인데 110개 부르면 select 쿼리만 나가고 count 쿼리는 안나감.
        // 마지막 페이지 일 때(offset + 컨텐츠 사이즈를 더해서 전체 사이즈 구할 때)
//        return PageableExecutionUtils.getPage(content, pageable, () -> countQuery.fetchCount());
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchCount);
    }

    // StringUtils.hasText()로 해도 됨.
    // 빈환 타입을 BooleanExpression으로 해두면 and로 where 연결 가능
    private BooleanExpression usernameEq(String username) {
        return isEmpty(username) ? null : member.username.eq(username);
    }

    private BooleanExpression teamNameEq(String teamName) {
        return hasText(teamName) ? team.name.eq(teamName) : null;
    }

    private BooleanExpression ageGoe(Integer ageGoe) {
        return ageGoe != null ? member.age.goe(ageGoe) : null;
    }

    private BooleanExpression ageLoe(Integer ageLoe) {
        return ageLoe != null ? member.age.loe(ageLoe) : null;
    }

}
