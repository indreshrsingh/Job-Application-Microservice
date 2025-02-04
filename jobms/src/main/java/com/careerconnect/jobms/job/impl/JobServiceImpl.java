package com.careerconnect.jobms.job.impl;


import com.careerconnect.jobms.job.Job;
import com.careerconnect.jobms.job.JobRepository;
import com.careerconnect.jobms.job.JobService;
import com.careerconnect.jobms.job.clients.CompanyClient;
import com.careerconnect.jobms.job.clients.ReviewClient;
import com.careerconnect.jobms.job.dto.JobDTO;
import com.careerconnect.jobms.job.external.Company;
import com.careerconnect.jobms.job.external.Review;
import com.careerconnect.jobms.job.mapper.JobMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class JobServiceImpl implements JobService {

    JobRepository jobRepository;
    @Autowired
    RestTemplate restTemplate;
    CompanyClient companyClient;
    ReviewClient reviewClient;
    public JobServiceImpl(JobRepository jobRepository,CompanyClient companyClient,ReviewClient reviewClient) {

        this.jobRepository = jobRepository;
        this.companyClient=companyClient;
        this.reviewClient=reviewClient;
    }

    private JobDTO convertToJobWithCompanyDTO(Job job) {
        Company company = companyClient.getCompany(job.getCompanyId());
        //Company company = restTemplate.getForObject("http://COMPANYMS:8081/companies/" + job.getCompanyId(), Company.class);
        ResponseEntity<List<Review>> reviewResponse= restTemplate.exchange("http://REVIEWMS:8083/reviews?companyId=" + job.getCompanyId(), HttpMethod.GET, null,
                new  ParameterizedTypeReference<List<Review>>(){});
        List<Review> reviews = reviewClient.getReviews(job.getCompanyId());
        JobDTO jobDTO = JobMapper.mapToJobWithCompanyDTO(job, company,reviews);
        return jobDTO;
    }


    @Override
    @CircuitBreaker(name="comapnyBreaker",fallbackMethod = "companyBreakerFallBackMethod")
    public List<JobDTO> findAll() {
        List<Job> jobs = jobRepository.findAll();
        List<JobDTO> jobDTOS = new ArrayList<>();

        for (Job job : jobs) {
            jobDTOS.add(convertToJobWithCompanyDTO(job));
        }

        return jobDTOS;
    }

    public List<String> companyBreakerFallBackMethod() {
        List<String> l=new ArrayList<>();
        l.add("Please try after some time");
        return l;
    }

    @Override
    public void createJob(Job job) {
        jobRepository.save(job);
    }

    @Override
    @CircuitBreaker(name="comapnyBreaker",fallbackMethod = "companyBreakerFallBackMethod")
    public JobDTO getJobById(Long id) {
        JobDTO jobDTO = new JobDTO();
        if (id == null) return null;
        if (jobRepository.findById(id).isEmpty()) return null;
        Job job = jobRepository.findById(id).get();
        return convertToJobWithCompanyDTO(job);
    }

    @Override
    public boolean deleteJobById(Long id) {
        try {
            jobRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean updateJobById(Long id, Job jobToUpdate) {
        Optional<Job> jobOptional = jobRepository.findById(id);
        if (jobOptional.isPresent()) {
            Job job = jobOptional.get();
            job.setTitle(jobToUpdate.getTitle());
            job.setDescription(jobToUpdate.getDescription());
            job.setMinSalary(jobToUpdate.getMinSalary());
            job.setMaxSalary(jobToUpdate.getMaxSalary());
            job.setLocation(jobToUpdate.getLocation());
            jobRepository.save(job);
            return true;
        }
        return false;
    }
}
