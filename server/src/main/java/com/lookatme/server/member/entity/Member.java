package com.lookatme.server.member.entity;

import com.lookatme.server.audit.BaseTimeEntity;
import com.lookatme.server.auth.utils.MemberAuthorityUtils;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Where;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Where(clause = "member_status not like 'MEMBER_WITHDRAWAL'")
@Table(
        uniqueConstraints = {
                @UniqueConstraint(name = "UniqueAccount", columnNames = {"email", "oauthPlatform"}),
                @UniqueConstraint(name = "UniqueNickname", columnNames = {"nickname"})
        }
)
@Entity
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long memberId;

    @Embedded
    private Account account;

    private String password;

    @ColumnDefault("0")
    private int loginTryCnt;

    private LocalDateTime lastLoginTime; // 최근 로그인 시간

    private String nickname; // Unique

    @ColumnDefault("0")
    private int height;

    @ColumnDefault("0")
    private int weight;

    @ColumnDefault("'MEMBER_ACTIVE'")
    @Enumerated(EnumType.STRING)
    private MemberStatus memberStatus;

    @Column(length = 1000)
    @ColumnDefault("'" + DEFAULT_PROFILE_IMG + "'")
    private String profileImageUrl;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> roles = new ArrayList<>();

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    private LocalDateTime updatedDate;

    private LocalDateTime deletedDate;

    @OneToMany(mappedBy = "from", cascade = CascadeType.REMOVE)
    private Set<Follow> followees = new HashSet<>(); // 내가 팔로우 한 사람

    @OneToMany(mappedBy = "to", cascade = CascadeType.REMOVE)
    private Set<Follow> followers = new HashSet<>(); // 나를 팔로우하는 사람

    @Transient
    private boolean follow; // 로그인 한 사용자가 팔로우 한 회원인지 유무 체크

    @Transient
    public static final String DEFAULT_PROFILE_IMG = "https://user-images.githubusercontent.com/74748851/212484014-b22c7726-1091-4b89-a9d5-c97d72b82068.png";

    @Builder
    public Member(long memberId, Account account, String password, String nickname, int height, int weight, String profileImageUrl) {
        this.memberId = memberId;
        this.account = account;
        this.password = password;
        this.nickname = nickname;
        this.height = height;
        this.weight = weight;
        this.profileImageUrl = profileImageUrl;
        this.memberStatus = MemberStatus.MEMBER_ACTIVE;
        this.roles = List.of("USER");
    }

    public void updateMemberProfile(String nickname, int height, int weight) {
        this.nickname = nickname;
        this.height = height;
        this.weight = weight;
    }

    public void setProfileImage(String imageUrl) {
        this.profileImageUrl = imageUrl;
    }

    // 비밀번호 저장 시 필수로 암호화 해서 저장하도록 하기 위함
    public void setPassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void setRoles(MemberAuthorityUtils authorityUtils) {
        this.roles = authorityUtils.createRoles(account.email);
    }

    public int loginFailed() {
        if(++loginTryCnt >= 5) {
            memberStatus = MemberStatus.MEMBER_LOCKED; // 로그인 5회 이상 실패 시 계정 잠김 상태로 변환
        }
        return loginTryCnt;
    }

    public void loginSuccess() {
        lastLoginTime = LocalDateTime.now();
        loginTryCnt = 0;
    }

    // 회원 탈퇴
    public void withdrawal() {
        this.memberStatus = MemberStatus.MEMBER_WITHDRAWAL;
        this.password = null;
        this.height = this.weight = 0;
        this.profileImageUrl = DEFAULT_PROFILE_IMG;
        this.deletedDate = LocalDateTime.now();
    }
    
    // 탈퇴한 회원인지 유무
    public boolean isDelete() {
        return memberStatus == MemberStatus.MEMBER_WITHDRAWAL;
    }

    // 내가 팔로우 중인 회원인지 유무
    public boolean isFollow() {
        return follow;
    }

    public int getFollowerCnt() {
        return followers.size();
    }

    public int getFolloweeCnt() {
        return followees.size();
    }

    public void setStatusToFollowingMember() {
        this.follow = true;
    }

    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return account.email;
    }

    public OauthPlatform getOauthPlatform() {
        return account.oauthPlatform;
    }

    public String getUniqueKey() {
        return account.toString();
    }

    public static Account uniqueKeyToAccount(String memberUniqueKey) {
        String[] split = memberUniqueKey.split("/");
        return new Account(split[0], OauthPlatform.valueOf(split[1]));
    }
}
